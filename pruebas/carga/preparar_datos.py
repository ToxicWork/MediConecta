"""
Datos para la prueba de carga (pruebas/carga/carga_api.js), sobre una base con los usuarios de demo:

  - la Dra. Ana Lopez publica 20 horarios a 40-45 días de hoy (lejos de los días que usan las otras pruebas), para
    que la búsqueda de disponibilidad devuelva una lista real;
  - Juan Perez (paciente de demo, con OSDE) reserva, paga el copago y confirma 3 de ellos, para que "mis turnos" y el
    detalle de un turno respondan con datos.

Se puede correr más de una vez (cada corrida suma horarios y turnos). Requisitos: los de las pruebas de punta a punta
(MediConecta y los sistemas externos desplegados, Google Chrome o Edge con NAVEGADOR=msedge, pip install playwright).
Uso:  python pruebas/carga/preparar_datos.py
"""
import json, os, random, ssl, sys, urllib.request
from datetime import date, timedelta
from playwright.sync_api import sync_playwright

urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))
try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = os.environ.get("MEDICONECTA_URL", "https://localhost:8443/mediconecta/")
PROFESIONAL, PACIENTE = "ana.lopez@mediconecta.test", "juan.perez@mediconecta.test"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
HORARIOS, TURNOS = 20, 3


def login(br, email):
    pg = br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", "demo1234")
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg


def mensaje(pg):
    return " ".join(pg.locator(".ui-growl-message").all_inner_texts())


def publicar(prof, fecha):
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", fecha)
    prof.keyboard.press("Escape")
    with prof.expect_navigation():
        prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
    return "Disponibilidad publicada" in mensaje(prof)


def reservar_y_pagar(pac, fecha):
    dia, hora = fecha.split(" ")
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    fila = pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora)
    if fila.count() == 0:
        return False
    with pac.expect_navigation():
        fila.locator("button:has-text('Reservar')").first.click()
    pac.fill("#formConfirmacion\\:titular", "Juan Perez")
    pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
    pac.fill("#formConfirmacion\\:vencimiento", "")
    pac.fill("#formConfirmacion\\:vencimiento", "12/29")
    pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
    pac.click("#formConfirmacion\\:btnConfirmar")
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    return "Turno confirmado" in pac.content()


publicados = []
with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    prof = login(br, PROFESIONAL)
    for _ in range(HORARIOS * 3):
        if len(publicados) == HORARIOS:
            break
        dia = (date.today() + timedelta(days=random.randint(40, 45))).strftime("%d/%m/%Y")
        fecha = "%s %02d:%02d" % (dia, random.randint(7, 19), random.randint(0, 11) * 5)
        if fecha not in publicados and publicar(prof, fecha):
            publicados.append(fecha)
    pac = login(br, PACIENTE)
    confirmados = [f for f in random.sample(publicados, min(TURNOS, len(publicados))) if reservar_y_pagar(pac, f)]
    br.close()

with urllib.request.urlopen(urllib.request.Request(B + "api/v1/auth/login", method="POST",
                            data=json.dumps({"email": PACIENTE, "password": "demo1234"}).encode(),
                            headers={"Content-Type": "application/json"}), timeout=15) as r:
    token = json.loads(r.read())["token"]
with urllib.request.urlopen(urllib.request.Request(B + "api/v1/turnos", headers={"Authorization": "Bearer " + token}),
                            timeout=15) as r:
    en_la_api = sum(1 for t in json.loads(r.read()) if t["estado"] == "CONFIRMADO")

print("Horarios publicados: %d de %d" % (len(publicados), HORARIOS))
print("Turnos confirmados para Juan Perez: %d de %d (la API muestra %d confirmados)" % (len(confirmados), TURNOS, en_la_api))
sys.exit(0 if len(publicados) == HORARIOS and len(confirmados) == TURNOS else 1)
