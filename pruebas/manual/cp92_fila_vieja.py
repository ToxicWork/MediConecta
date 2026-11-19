"""Reproducción del D-45 (CP-92, 19/11): con la Agenda abierta desde antes, el paciente cancela un turno que la profesional ya canceló.
Imprime qué turnos quedaron cancelados según la API del paciente."""
import json, random, ssl, sys, urllib.request
from datetime import date, timedelta
from playwright.sync_api import sync_playwright

sys.stdout.reconfigure(encoding="utf-8")
urllib.request.install_opener(urllib.request.build_opener(urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))
B = "https://localhost:8443/mediconecta/"


def api(ruta, metodo="GET", cuerpo=None, token=None):
    r = urllib.request.Request(B + "api/v1/" + ruta, method=metodo, data=None if cuerpo is None else json.dumps(cuerpo).encode())
    r.add_header("Content-Type", "application/json")
    if token:
        r.add_header("Authorization", "Bearer " + token)
    with urllib.request.urlopen(r, timeout=30) as resp:
        return json.loads(resp.read().decode() or "null")


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


with sync_playwright() as p:
    br = p.chromium.launch(channel="msedge", headless=True)
    s = random.randint(10000, 99999)
    email, nombre = "filavieja.%d@mediconecta.test" % s, "Paciente Fila Vieja %d" % s
    pg = pagina(br)
    pg.goto(B + "registro.xhtml")
    for c, v in (("nombre", nombre), ("email", email), ("password", "demo1234"), ("repeticion", "demo1234")):
        pg.fill("#formRegistro\\:" + c, v)
    with pg.expect_navigation():
        pg.click("#formRegistro button:has-text('Registrarme')")
    prof = login(br, "ana.lopez@mediconecta.test")
    pac = login(br, email)
    dia = (date.today() + timedelta(days=random.randint(55, 60))).strftime("%d/%m/%Y")
    horas = ["%02d:%02d" % (random.randint(7, 9), random.randint(0, 11) * 5), "%02d:%02d" % (random.randint(15, 18), random.randint(0, 11) * 5)]
    for h in horas:
        prof.goto(B + "agenda.xhtml")
        prof.fill("#formPublicar\\:fecha_input", dia + " " + h)
        prof.keyboard.press("Escape")
        with prof.expect_navigation():
            prof.locator("#formPublicar button:has-text('Publicar')").evaluate("b => b.click()")
        pac.goto(B + "agenda.xhtml")
        sel = pac.locator("#formAgenda\\:profesional")
        sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
        with pac.expect_navigation():
            pac.click("button:has-text('Ver turnos disponibles')")
        with pac.expect_navigation():
            pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + h).locator("button:has-text('Reservar')").first.click()
        pac.fill("#formConfirmacion\\:titular", "Fila Vieja")
        pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
        pac.fill("#formConfirmacion\\:vencimiento", "")
        pac.fill("#formConfirmacion\\:vencimiento", "12/29")
        pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
        pac.click("#formConfirmacion\\:btnConfirmar")
        with pac.expect_navigation():
            pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
        print("pago de las", h, ":", pac.locator(".ui-growl-message").all_inner_texts())
        pac.wait_for_timeout(1500)
    print("turnos confirmados:", dia, horas)
    pac.goto(B + "agenda.xhtml")                      # la página del paciente queda abierta con los dos turnos
    temprano = pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=horas[0])
    print("el paciente ve las dos filas:", pac.locator("#formAgenda\\:tablaMisTurnos tbody tr").count())
    prof.goto(B + "agenda.xhtml")                     # la profesional cancela el de la mañana
    fila = prof.locator("#formTurnosProfesional\\:tablaConfirmadosProfesional tr", has_text=nombre).filter(has_text=horas[0])
    fila.locator("button:has-text('Cancelar')").click()
    with prof.expect_navigation():
        prof.click(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')")
    print("profesional:", prof.locator(".ui-growl-message").all_inner_texts())
    temprano.locator("button:has-text('Cancelar')").click()   # el paciente, con la página vieja, cancela el de la mañana
    print("el diálogo le pregunta:", pac.locator(".ui-confirm-dialog:visible").inner_text().replace("\n", " ")[:160])
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')")
    print("paciente:", pac.locator(".ui-growl-message").all_inner_texts())
    token = api("auth/login", "POST", {"email": email, "password": "demo1234"})["token"]
    for t in api("turnos", token=token):
        print("API:", t["fechaHora"], t["estado"], "cancelado por", t.get("canceladoPor"))
    br.close()
