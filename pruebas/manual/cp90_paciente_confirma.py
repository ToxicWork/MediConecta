"""CP-90 (manual, 19/11), el "otro navegador" mientras la profesional mira su bandeja en Chrome: en Edge (Playwright), la Dra. Ana Lopez publica un horario y un paciente nuevo lo
reserva y lo paga. Imprime la hora exacta (epoch ms) en que el paciente ve "Turno confirmado"."""
import random, sys, time
from datetime import date, timedelta
from playwright.sync_api import sync_playwright

B = "https://localhost:8443/mediconecta/"


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
    sufijo = random.randint(10000, 99999)
    email = "cp90.%d@mediconecta.test" % sufijo
    nuevo = pagina(br)
    nuevo.goto(B + "registro.xhtml")
    for campo, valor in (("nombre", "Paciente CP90 %d" % sufijo), ("email", email), ("password", "demo1234"),
                         ("repeticion", "demo1234"), ("obraSocial", "")):
        nuevo.fill("#formRegistro\\:" + campo, valor)
    with nuevo.expect_navigation():
        nuevo.click("#formRegistro button:has-text('Registrarme')")
    prof = login(br, "ana.lopez@mediconecta.test")
    for _ in range(15):
        dia = (date.today() + timedelta(days=random.randint(40, 46))).strftime("%d/%m/%Y")
        fecha = "%s %02d:%02d" % (dia, random.randint(7, 19), random.randint(0, 11) * 5)
        prof.goto(B + "agenda.xhtml")
        prof.fill("#formPublicar\\:fecha_input", fecha)
        prof.keyboard.press("Escape")
        with prof.expect_navigation():
            prof.locator("#formPublicar button:has-text('Publicar')").evaluate("b => b.click()")
        if any("Disponibilidad publicada" in m for m in prof.locator(".ui-growl-message").all_inner_texts()):
            break
    pac = login(br, email)
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    d, h = fecha.split(" ")
    with pac.expect_navigation():
        pac.locator("#formAgenda\\:tablaTurnos tr", has_text=d + " · " + h).locator("button:has-text('Reservar')").first.click()
    pac.fill("#formConfirmacion\\:titular", "Paciente CP90")
    pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
    pac.fill("#formConfirmacion\\:vencimiento", "")
    pac.fill("#formConfirmacion\\:vencimiento", "12/29")
    pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
    pac.click("#formConfirmacion\\:btnConfirmar")
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    confirmado = int(time.time() * 1000)
    texto = " ".join(pac.locator(".ui-growl-message").all_inner_texts())
    print("turno", fecha, "| paciente", email, "| confirmado_ms", confirmado, "|", texto[:120])
    br.close()
