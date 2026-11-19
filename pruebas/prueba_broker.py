"""Prueba de resiliencia del broker: con el broker caído y vuelto a levantar no se pierde ningún mensaje.

  1. Con la cola de autorizaciones en pausa (jboss-cli), el paciente confirma un turno con OSDE: la solicitud de
     autorización queda esperando en el broker, guardada en su journal persistente (la cola es durable).
  2. Se reinicia el servidor con jboss-cli (:reload): se bajan y se vuelven a levantar la aplicación y el broker
     embebido (ActiveMQ Artemis) sin que nadie haya consumido la solicitud.
  3. Al volver, la pausa ya no está (no sobrevive al reinicio) y la solicitud se procesa sola: el turno queda con la
     autorización de la obra social, sin que nadie la reenvíe y sin pasar por la DLQ.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados, Google
Chrome (o Edge con NAVEGADOR=msedge),  pip install playwright  y WILDFLY_HOME apuntando a la instalación de
WildFly (pausa la cola y reinicia el servidor con jboss-cli).
Uso:  python pruebas/prueba_broker.py
Atención: reinicia el servidor (unos segundos): no correrla mientras alguien está usando la aplicación.
Deja en la base un paciente de prueba por corrida (broker.NNNNN@mediconecta.test, OSDE) con un turno confirmado y
autorizado con la Dra. Ana Lopez.
"""
import json, os, random, re, ssl, subprocess, sys, time, urllib.error, urllib.request
from datetime import date, timedelta
from playwright.sync_api import sync_playwright

# MediConecta corre por HTTPS con el certificado autofirmado que WildFly genera para localhost: se acepta sin validar.
urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))
try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "https://localhost:8443/mediconecta/"
EXT = "http://localhost:8080/externos/"
PROFESIONAL = "ana.lopez@mediconecta.test"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
WILDFLY_HOME = os.environ.get("WILDFLY_HOME")
COLA = "/subsystem=messaging-activemq/server=default/jms-queue=autorizaciones"
DLQ = "/subsystem=messaging-activemq/server=default/jms-queue=DLQ"
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def dia_desde_hoy(dias):
    """Un día a tantos días de hoy ("dd/mm/aaaa"): la prueba no depende de la fecha en que se corre."""
    return (date.today() + timedelta(days=dias)).strftime("%d/%m/%Y")


def jboss_cli(comando):
    script = os.path.join(WILDFLY_HOME, "bin", "jboss-cli.bat" if os.name == "nt" else "jboss-cli.sh")
    return subprocess.run([script, "--connect", "--command=" + comando], capture_output=True, text=True, timeout=90,
                          stdin=subprocess.DEVNULL, env={**os.environ, "NOPAUSE": "true"}).stdout


def exito(salida):
    return '"outcome" => "success"' in salida


def mensajes_en(cola):
    """Cuántos mensajes tiene la cola en el broker (operación count-messages)."""
    numero = re.search(r'"result" => (\d+)', jboss_cli(cola + ":count-messages"))
    return int(numero.group(1)) if numero else -1


def consola(**modos):
    pedido = urllib.request.Request(EXT + "api/consola/estado", method="PUT", data=json.dumps(modos).encode(),
                                    headers={"Content-Type": "application/json"})
    urllib.request.urlopen(pedido, timeout=10)


def api(ruta, metodo="GET", cuerpo=None, token=None):
    pedido = urllib.request.Request(B + "api/v1/" + ruta, method=metodo,
                                    data=None if cuerpo is None else json.dumps(cuerpo).encode())
    pedido.add_header("Content-Type", "application/json")
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(pedido, timeout=15) as respuesta:
            return respuesta.status, json.loads(respuesta.read().decode() or "null")
    except urllib.error.HTTPError as error:
        return error.code, None


def responde():
    """¿La aplicación ya atiende? (después del reinicio)."""
    try:
        with urllib.request.urlopen(B + "login.xhtml", timeout=5) as respuesta:
            return respuesta.status == 200
    except Exception:  # noqa: BLE001 - mientras reinicia, cualquier error de conexión es "todavía no"
        return False


def pagina(br):
    return br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()


def login(br, email):
    pg = pagina(br)
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", "demo1234")
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg


def mensaje(pg):
    return " ".join(pg.locator(".ui-growl-message").all_inner_texts())


if not WILDFLY_HOME:
    sys.exit("Definí WILDFLY_HOME: la prueba pausa la cola y reinicia el servidor con jboss-cli.")

pausada = False
try:
    with sync_playwright() as p:
        br = p.chromium.launch(channel=NAVEGADOR, headless=True)
        consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR", demoraAutorizacionMs=1500)
        sufijo = random.randint(10000, 99999)
        email = "broker.%d@mediconecta.test" % sufijo
        registro = pagina(br)
        registro.goto(B + "registro.xhtml")
        for campo, valor in (("nombre", "Paciente Broker %d" % sufijo), ("email", email), ("password", "demo1234"),
                             ("repeticion", "demo1234"), ("obraSocial", "OSDE 210"), ("nroAfiliado", str(sufijo))):
            registro.fill("#formRegistro\\:" + campo, valor)
        with registro.expect_navigation():
            registro.click("#formRegistro button:has-text('Registrarme')")
        registro.context.close()

        prof = login(br, PROFESIONAL)
        for _ in range(15):  # un horario libre (D-23 no deja repetir uno de otra corrida)
            # Minutos de a 5: el selector de fecha (stepMinute="5") redondea lo que no lo es.
            fecha = "%s %02d:%02d" % (dia_desde_hoy(random.randint(42, 48)), random.randint(7, 19), random.randint(0, 11) * 5)
            prof.goto(B + "agenda.xhtml")
            prof.fill("#formPublicar\\:fecha_input", fecha)
            prof.keyboard.press("Escape")
            with prof.expect_navigation():
                prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
            if "Disponibilidad publicada" in mensaje(prof):
                break
        prof.context.close()
        dia, hora = fecha.split(" ")

        # ---- 1. La solicitud queda esperando en el broker -----------------------------------------------
        print("-- 1. Con la cola de autorizaciones en pausa, el paciente confirma un turno con OSDE")
        en_dlq = mensajes_en(DLQ)
        pausada = exito(jboss_cli(COLA + ":pause"))
        check("1. la cola de autorizaciones queda en pausa (jboss-cli)", pausada)
        antes = mensajes_en(COLA)
        pac = login(br, email)
        pac.goto(B + "agenda.xhtml")
        sel = pac.locator("#formAgenda\\:profesional")
        sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
        with pac.expect_navigation():
            pac.click("button:has-text('Ver turnos disponibles')")
        with pac.expect_navigation():
            pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Reservar')").first.click()
        if pac.locator("#formConfirmacion\\:titular").count():  # copago con OSDE: se paga con tarjeta
            pac.fill("#formConfirmacion\\:titular", "Paciente Broker")
            pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
            pac.fill("#formConfirmacion\\:vencimiento", "")
            pac.fill("#formConfirmacion\\:vencimiento", "12/29")
            pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
        pac.click("#formConfirmacion\\:btnConfirmar")
        with pac.expect_navigation():
            pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
        check("1. el paciente confirma y paga el copago (%s)" % fecha, "Turno confirmado" in pac.content())
        pac.context.close()
        br.close()

    token = api("auth/login", "POST", {"email": email, "password": "demo1234"})[1]["token"]
    iso = "%s-%s-%sT%s" % (dia[6:], dia[3:5], dia[:2], hora)
    turno = next(t for t in api("turnos", token=token)[1] if t["fechaHora"].startswith(iso))
    esperando = antes
    for _ in range(10):
        esperando = mensajes_en(COLA)
        if esperando > antes:
            break
        time.sleep(1)
    check("1. la solicitud de autorización queda en la cola, sin consumir (%d mensaje(s); antes %d)" % (esperando, antes),
          esperando > antes)
    time.sleep(3)  # el MDB no la toma mientras la cola está en pausa
    check("1. y el turno sigue con la autorización pendiente (%s)" % turno["autorizacion"],
          api("turnos/%d" % turno["id"], token=token)[1]["autorizacion"] == "PENDIENTE")

    # ---- 2. Se cae el broker (y el servidor) y vuelve a levantar -----------------------------------------
    print("-- 2. Se reinicia el servidor con el broker embebido")
    inicio = time.time()
    check("2. jboss-cli reinicia el servidor (:reload)", exito(jboss_cli(":reload")))
    pausada = False  # la pausa no sobrevive al reinicio
    time.sleep(3)
    while not responde() and time.time() - inicio < 180:
        time.sleep(2)
    check("2. la aplicación vuelve a atender (%.0f s)" % (time.time() - inicio), responde())

    # ---- 3. La solicitud se procesa sola -----------------------------------------------------------------
    print("-- 3. Después del reinicio")
    # Un token nuevo: si el secreto del JWT no está fijado en el servidor, se genera otro al reiniciar.
    token = api("auth/login", "POST", {"email": email, "password": "demo1234"})[1]["token"]
    estado, fin = None, time.time() + 90
    while time.time() < fin:
        codigo, cuerpo = api("turnos/%d" % turno["id"], token=token)
        estado = cuerpo["autorizacion"] if codigo == 200 and cuerpo else estado
        if estado == "AUTORIZADA":
            break
        time.sleep(2)
    check("3. la solicitud que esperaba en el broker se procesa sola: el turno queda %s" % estado, estado == "AUTORIZADA")
    check("3. la cola queda vacía (%d mensaje(s))" % mensajes_en(COLA), mensajes_en(COLA) == 0)
    check("3. y no pasó por la DLQ (%d mensaje(s), igual que antes)" % mensajes_en(DLQ), mensajes_en(DLQ) == en_dlq)
finally:
    if pausada:  # si algo falló antes del reinicio, la cola no queda en pausa
        jboss_cli(COLA + ":resume")

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
