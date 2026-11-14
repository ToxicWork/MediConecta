"""Verifica el vencimiento del hold (HU-01): un turno retenido y no confirmado vuelve a ofrecerse solo.

  1. La profesional publica un horario.
  2. El paciente lo reserva: deja de aparecer en la disponibilidad pública (API REST).
  3. Nadie confirma. Pasados los 5 minutos del hold, el horario vuelve a estar disponible.

Tarda entre 5 y 7 minutos porque espera el vencimiento real. Requisitos: los de prueba_entrega4.py.
Uso:  python pruebas/prueba_hold_vence.py
"""
import json, random, sys, time, urllib.request
from playwright.sync_api import sync_playwright

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "http://localhost:8080/mediconecta/"
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
    pg = br.new_context(viewport={"width": 1366, "height": 900}).new_page()
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", "demo1234")
    pg.click("#formLogin button:has-text('Ingresar')")
    pg.wait_for_url("**/agenda.xhtml*")
    return pg


fecha = "%02d/12/2026 %02d:%02d" % (random.randint(14, 28), random.randint(7, 19), random.choice([5, 15, 25, 35, 45, 55]))
dia, hora = fecha.split(" ")
iso = "%s-%s-%sT%s" % (dia[6:], dia[3:5], dia[:2], hora)
ana = next(p["id"] for p in api("profesionales") if "Ana Lopez" in p["nombre"])


def ofrecido():
    return any(t["fechaHora"].startswith(iso) for t in api("profesionales/%s/disponibilidad" % ana))


with sync_playwright() as p:
    br = p.chromium.launch(channel="chrome", headless=True)
    prof = login(br, PROFESIONAL)
    prof.fill("#formPublicar\\:fecha_input", fecha)
    prof.keyboard.press("Escape")
    with prof.expect_navigation():
        prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
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
    br.close()

print("RESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
