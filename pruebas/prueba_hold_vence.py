"""Verifica el vencimiento del hold (HU-01): un turno retenido y no confirmado vuelve a ofrecerse solo.

  1. La profesional publica un horario.
  2. El paciente lo reserva: deja de aparecer en la disponibilidad pública (API REST).
  3. Nadie confirma. Pasados los 5 minutos del hold, el horario vuelve a estar disponible.
  4. D-33: al vencer la conversación (@StatefulTimeout), el @PreDestroy no falla. Lo verifica en el log del servidor
     si está definida WILDFLY_HOME (sin ella, avisa que no lo puede verificar).

Tarda entre 5 y 7 minutos porque espera el vencimiento real. Requisitos: los de prueba_entrega4.py.
Uso:  python pruebas/prueba_hold_vence.py
"""
import os
import json, random, sys, time, urllib.request
from datetime import date, timedelta
from playwright.sync_api import sync_playwright
import ssl
# MediConecta corre por HTTPS con el certificado autofirmado que WildFly genera para localhost (SEC-W04):
# las pruebas lo aceptan sin validarlo. Sólo sirve para el entorno local.
urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "https://localhost:8443/mediconecta/"
WILDFLY_HOME = os.environ.get("WILDFLY_HOME")
LOG_SERVIDOR = os.path.join(WILDFLY_HOME, "standalone", "log", "server.log") if WILDFLY_HOME else None
INICIO_LOG = os.path.getsize(LOG_SERVIDOR) if LOG_SERVIDOR and os.path.exists(LOG_SERVIDOR) else 0
PACIENTE, PROFESIONAL = "juan.perez@mediconecta.test", "ana.lopez@mediconecta.test"
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre, flush=True)


def api(ruta):
    with urllib.request.urlopen(B + "api/v1/" + ruta, timeout=15) as r:
        return json.loads(r.read().decode("utf-8"))


def login(br, email):
    pg = br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", "demo1234")
    pg.click("#formLogin button:has-text('Ingresar')")
    pg.wait_for_url("**/agenda.xhtml*")
    return pg


ana = next(p["id"] for p in api("profesionales") if "Ana Lopez" in p["nombre"])


def dia_desde_hoy(dias):
    """Un día a tantos días de hoy ("dd/mm/aaaa"): la prueba no depende de la fecha en que se corre."""
    return (date.today() + timedelta(days=dias)).strftime("%d/%m/%Y")


def elegir_fecha():
    global fecha, dia, hora, iso
    fecha = "%s %02d:%02d" % (dia_desde_hoy(random.randint(26, 40)), random.randint(7, 19), random.randint(0, 11) * 5)
    dia, hora = fecha.split(" ")
    iso = "%s-%s-%sT%s" % (dia[6:], dia[3:5], dia[:2], hora)


elegir_fecha()


def ofrecido():
    return any(t["fechaHora"].startswith(iso) for t in api("profesionales/%s/disponibilidad" % ana))


with sync_playwright() as p:
    br = p.chromium.launch(channel=os.environ.get("NAVEGADOR", "chrome"), headless=True)
    prof = login(br, PROFESIONAL)
    # Un horario libre: si ya existe uno igual de otra corrida (D-23 no lo deja repetir), se elige otro.
    for _ in range(15):
        prof.goto(B + "agenda.xhtml")
        prof.fill("#formPublicar\\:fecha_input", fecha)
        prof.keyboard.press("Escape")
        with prof.expect_navigation():
            prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
        if "Disponibilidad publicada" in prof.content():
            break
        elegir_fecha()
    check("la profesional publica el horario " + fecha + " y la API lo ofrece", ofrecido())

    pac = login(br, PACIENTE)
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    pac.click("button:has-text('Ver turnos disponibles')")
    pac.wait_for_load_state()
    pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Reservar')").click()
    pac.wait_for_load_state()
    inicio = time.time()
    check("el paciente lo reserva: queda en espera a su nombre", "Turno reservado temporalmente" in pac.content())
    check("mientras está en espera no se ofrece a otros pacientes", not ofrecido())

    time.sleep(240)
    check("a los 4 minutos sigue retenido", not ofrecido())
    liberado = False
    while time.time() - inicio < 450 and not liberado:
        time.sleep(15)
        liberado = ofrecido()
    check("sin confirmar, el horario vuelve a ofrecerse solo (a los %d s de la reserva)" % (time.time() - inicio), liberado)

    # D-33: la conversación del paciente vence 5 minutos después de su último pedido; se le da un minuto más al
    # contenedor para destruirla y se revisa que el @PreDestroy no haya fallado.
    if LOG_SERVIDOR:
        time.sleep(60)
        with open(LOG_SERVIDOR, encoding="utf-8", errors="replace") as log:
            log.seek(INICIO_LOG)
            nuevo = log.read()
        fallas = [l for l in nuevo.splitlines() if "WFLYEE0006" in l and "ServicioDeTurnosBean" in l]
        check("D-33. al vencer la conversación, el @PreDestroy del componente con estado no falla%s"
              % ("" if not fallas else " (" + fallas[0][:110] + ")"), not fallas)
        print("     lo liberó", "el contenedor (@PreDestroy)" if "el contenedor liberó el turno" in nuevo
              else "el temporizador de respaldo (LiberadorDeHolds), que llegó primero")
    else:
        print("     D-33: definí WILDFLY_HOME para revisar el log del servidor")
    br.close()

print("RESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
