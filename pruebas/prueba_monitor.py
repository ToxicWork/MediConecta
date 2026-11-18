"""Prueba de la HU-09: Monitor de eventos (paciente y profesional) sobre la app desplegada.

  1. El paciente confirma un turno y en el Monitor ve el aviso procesado por Notificaciones y Auditoría, cada uno
     con su latencia (Facturación "no aplica": su selector no recibe confirmaciones).
  2. Suscriptor caído: con el MDB de Auditoría frenado (jboss-cli), el paciente cancela; el aviso figura
     "Pendiente" en Auditoría y procesado en Notificaciones y Facturación. Al reactivarlo, Auditoría pasa a
     procesado sola, sin recargar la página.
  3. Titularidad y roles: un paciente nuevo no ve avisos ajenos; el administrativo no tiene el Monitor.
  4. Carga de a tandas: la profesional ve 10 avisos por página y la página 2 trae otros.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados, Google
Chrome (o Edge con NAVEGADOR=msedge),  pip install playwright  y WILDFLY_HOME apuntando a la instalación de
WildFly (el punto 2 frena y reactiva el MDB con jboss-cli).
Uso:  python pruebas/prueba_monitor.py
Deja en la base dos pacientes de prueba por corrida (monitor.NNNNN y monitor.otro.NNNNN@mediconecta.test) y un
turno cancelado de la Dra. Ana Lopez.
"""
import json, os, random, re, ssl, subprocess, sys, time, urllib.request
from playwright.sync_api import sync_playwright

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "https://localhost:8443/mediconecta/"
EXT = "http://localhost:8080/externos/"
PROFESIONAL, ADMINISTRATIVO = "ana.lopez@mediconecta.test", "carla.admin@mediconecta.test"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
WILDFLY_HOME = os.environ.get("WILDFLY_HOME")
MDB_AUDITORIA = "/deployment=mediconecta.war/subsystem=ejb3/message-driven-bean=AuditoriaSubscriber"
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def jboss_cli(comando):
    script = os.path.join(WILDFLY_HOME, "bin", "jboss-cli.bat" if os.name == "nt" else "jboss-cli.sh")
    return subprocess.run([script, "--connect", "--command=" + comando], capture_output=True, text=True, timeout=90,
                          stdin=subprocess.DEVNULL, env={**os.environ, "NOPAUSE": "true"}).stdout


def consola(**modos):
    pedido = urllib.request.Request(EXT + "api/consola/estado", method="PUT", data=json.dumps(modos).encode(),
                                    headers={"Content-Type": "application/json"})
    urllib.request.urlopen(pedido, timeout=10)


def login(br, email, clave="demo1234"):
    pg = br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", clave)
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg


def registrar(br, email, nombre):
    pg = br.new_context(ignore_https_errors=True).new_page()
    pg.goto(B + "registro.xhtml")
    pg.fill("#formRegistro\\:nombre", nombre)
    pg.fill("#formRegistro\\:email", email)
    pg.fill("#formRegistro\\:password", "demo1234")
    pg.fill("#formRegistro\\:repeticion", "demo1234")
    with pg.expect_navigation():
        pg.click("#formRegistro button:has-text('Registrarme')")
    pg.context.close()


def filas(pg):
    """Filas visibles del Monitor (sin recargar), como texto de una línea."""
    return [f.replace("\t", " | ").replace("\n", " ")
            for f in pg.locator("#formMonitor\\:tablaEventos_data tr").all_inner_texts()]


def esperar_fila(pg, condicion, segundos=20):
    """Recarga el Monitor hasta que la primera fila cumpla la condición (los avisos son asincrónicos)."""
    fin = time.time() + segundos
    while True:
        pg.goto(B + "monitor.xhtml")
        primera = (filas(pg) or [""])[0]
        if condicion(primera) or time.time() > fin:
            return primera
        time.sleep(2)


def reporte_paginador(pg):
    loc = pg.locator("#formMonitor\\:tablaEventos .ui-paginator-current")
    return loc.first.inner_text() if loc.count() else ""


with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR")
    sufijo = random.randint(10000, 99999)
    email = "monitor.%d@mediconecta.test" % sufijo
    registrar(br, email, "Paciente Monitor %d" % sufijo)

    # ---- 1. Aviso procesado, con latencia ------------------------------------------------------
    prof = login(br, PROFESIONAL)
    check("1. la profesional tiene el Monitor de eventos en el menú", prof.locator("a.nav-item", has_text="Monitor de eventos").count() == 1)
    # Un horario libre: si ya existe uno igual de otra corrida (D-23 no lo deja repetir), se elige otro.
    for _ in range(15):
        fecha = "%02d/12/2026 %02d:%02d" % (random.randint(20, 28), random.randint(7, 19), random.randint(0, 59))
        prof.goto(B + "agenda.xhtml")
        prof.fill("#formPublicar\\:fecha_input", fecha)
        prof.keyboard.press("Escape")
        with prof.expect_navigation():
            prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
        if "Disponibilidad publicada" in prof.content():
            break
    dia, hora = fecha.split(" ")

    pac = login(br, email)
    pac.goto(B + "monitor.xhtml")
    check("1. un paciente sin turnos ve el Monitor vacío, con el aviso de cuándo aparecen",
          "Todavía no hay avisos de tus turnos" in pac.content())
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    with pac.expect_navigation():
        pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Reservar')").first.click()
    pac.fill("#formConfirmacion\\:titular", "Paciente Monitor")
    pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
    pac.fill("#formConfirmacion\\:vencimiento", ""); pac.fill("#formConfirmacion\\:vencimiento", "12/29")
    pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
    pac.click("#formConfirmacion\\:btnConfirmar")
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    check("1. el paciente confirma un turno (%s)" % fecha, "Turno confirmado" in pac.content())

    fila = esperar_fila(pac, lambda f: "Confirmado" in f and f.count(" ms") >= 2)
    print("     fila:", fila)
    check("1. en el Monitor, el aviso de confirmación está procesado por Notificaciones y Auditoría, con su latencia",
          "Confirmado" in fila and dia in fila and len(re.findall(r"✓ \d+ ms", fila)) == 2)
    check("1. y Facturación figura «no aplica» (su selector no recibe confirmaciones)", "no aplica" in fila)

    # ---- 2. Suscriptor caído y recuperación sin recargar ---------------------------------------
    if not WILDFLY_HOME:
        check("2. definí WILDFLY_HOME para frenar el MDB de Auditoría con jboss-cli", False)
    else:
        try:
            check("2. se frena el MDB de Auditoría (jboss-cli stop-delivery)", "success" in jboss_cli(MDB_AUDITORIA + ":stop-delivery"))
            pac.goto(B + "agenda.xhtml")
            pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Cancelar')").first.click()
            with pac.expect_navigation():
                pac.locator(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')").click()
            fila = esperar_fila(pac, lambda f: "Cancelado" in f and f.count(" ms") >= 2)
            print("     fila:", fila)
            check("2. con Auditoría frenada, el aviso de cancelación figura Pendiente en Auditoría y procesado en las otras dos",
                  "Cancelado" in fila and "Pendiente" in fila and len(re.findall(r"✓ \d+ ms", fila)) == 2)
        finally:
            arrancado = "success" in jboss_cli(MDB_AUDITORIA + ":start-delivery")
        check("2. se reactiva el MDB de Auditoría", arrancado)
        pac.wait_for_timeout(9000)  # el p:poll refresca cada 5 s
        fila = (filas(pac) or [""])[0]
        print("     fila (sin recargar):", fila)
        check("2. sin recargar la página, Auditoría pasa a procesado sola (el broker le guardó el aviso)",
              "Cancelado" in fila and "Pendiente" not in fila and len(re.findall(r"✓ \d+ ms", fila)) == 3)

    # ---- 3. Titularidad y roles ------------------------------------------------------------------
    email_otro = "monitor.otro.%d@mediconecta.test" % sufijo
    registrar(br, email_otro, "Otro Paciente %d" % sufijo)
    otro = login(br, email_otro)
    otro.goto(B + "monitor.xhtml")
    check("3. otro paciente no ve los avisos ajenos", "Todavía no hay avisos de tus turnos" in otro.content() and not filas(otro)[1:])
    otro.context.close()
    adm = login(br, ADMINISTRATIVO)
    check("3. el administrativo no tiene el Monitor en el menú", adm.locator("a.nav-item", has_text="Monitor de eventos").count() == 0)
    adm.goto(B + "monitor.xhtml")
    check("3. y si escribe la URL vuelve a su inicio (Pagos)", "pagos.xhtml" in adm.url)
    adm.context.close()

    # ---- 4. Carga de a tandas ---------------------------------------------------------------------
    prof.goto(B + "monitor.xhtml")
    reporte = reporte_paginador(prof)
    total = int(re.search(r"de (\d+)", reporte).group(1)) if re.search(r"de (\d+)", reporte) else 0
    pagina1 = filas(prof)
    print("     paginador:", reporte)
    check("4. la profesional ve una página de 10 avisos (de %d en total)" % total, len(pagina1) == 10 and total > 10
          and reporte.startswith("1–10"))
    with prof.expect_response(lambda r: "monitor.xhtml" in r.url and r.request.method == "POST"):
        prof.locator("#formMonitor\\:tablaEventos .ui-paginator-next").first.click()
    prof.wait_for_function("document.querySelector('#formMonitor\\\\:tablaEventos .ui-paginator-current').innerText.startsWith('11')")
    pagina2 = filas(prof)
    check("4. «siguientes» trae la página 2 por AJAX, con otros avisos (%s)" % reporte_paginador(prof),
          pagina2 and pagina2 != pagina1 and reporte_paginador(prof).startswith("11–"))
    prof.context.close()
    pac.context.close()
    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
