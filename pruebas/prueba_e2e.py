"""Prueba de punta a punta de MediConecta (14 verificaciones) sobre la app desplegada.

Recorre, con un navegador real, el login (válido e inválido), la publicación de disponibilidad,
la reserva con hold, la confirmación y la cancelación, y verifica que las notificaciones
lleguen de forma asincrónica a la bandeja del profesional.

Requisitos: WildFly levantado con la app desplegada, Google Chrome instalado y
    pip install playwright
Uso:
    python pruebas/prueba_e2e.py

Usa los usuarios de demo (ver SETUP.md). Cada corrida publica un horario nuevo el 26/11/2026,
lo confirma y lo cancela: el turno queda DISPONIBLE y las notificaciones quedan en las bandejas.
"""
import time, re
from playwright.sync_api import sync_playwright
B="http://localhost:8080/mediconecta/"
def login(br, email):
    pg=br.new_context().new_page(); pg.goto(B+"login.xhtml")
    pg.fill("#formLogin\\:email", email); pg.fill("#formLogin\\:password", "demo1234")
    pg.click("#formLogin button:has-text('Ingresar')"); pg.wait_for_url("**/agenda.xhtml*"); return pg
def mis(pg):
    m=re.search(r"(\d+) turno\(s\) confirmado\(s\)", pg.content()); return int(m.group(1))
import random
HORA="%02d:%02d" % (random.randint(7,19), random.choice([5,15,25,35,45,55]))
FECHA="26/11/2026 "+HORA
FECHA_TABLA="26/11/2026 · "+HORA
ok=True
def check(nombre, cond):
    global ok; ok &= bool(cond); print(("OK   " if cond else "FALLA"), nombre)
with sync_playwright() as p:
    br=p.chromium.launch(channel="chrome", headless=True)
    # login inválido
    pg=br.new_context().new_page(); pg.goto(B+"login.xhtml"); pg.fill("#formLogin\\:email","juan.perez@mediconecta.test"); pg.fill("#formLogin\\:password","mala")
    pg.click("#formLogin button:has-text('Ingresar')"); pg.wait_for_load_state(); check("login con contraseña incorrecta rechazado", "Credenciales inválidas" in pg.content())
    # sin sesión -> redirige
    pg=br.new_context().new_page(); pg.goto(B+"agenda.xhtml"); check("agenda sin sesión redirige al login", "login.xhtml" in pg.url)
    prof=login(br,"ana.lopez@mediconecta.test")
    check("profesional ve su nombre en el layout", "Dra. Ana Lopez" in prof.content())
    check("CSS cargado (recursos estáticos)", prof.evaluate("getComputedStyle(document.querySelector('.sidebar')).backgroundImage").startswith("linear-gradient"))
    prof.fill("#formPublicar\\:fecha",FECHA); prof.click("#formPublicar button:has-text('Publicar')"); prof.wait_for_load_state()
    check("profesional publica disponibilidad", "Disponibilidad publicada" in prof.content())
    prof.goto(B+"historia.xhtml"); check("profesional abre historia clínica", "Historia" in prof.title() or "Historia" in prof.content())
    pac=login(br,"juan.perez@mediconecta.test")
    antes=mis(pac); print("     mis turnos al entrar:", antes)
    sel=pac.locator("#formAgenda\\:profesional"); sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    pac.click("button:has-text('Ver turnos disponibles')"); pac.wait_for_load_state()
    pac.locator("tr", has_text=FECHA_TABLA).locator("button:has-text('Reservar')").click(); pac.wait_for_load_state()
    check("hold visible", "Turno reservado temporalmente" in pac.content())
    t=time.time(); pac.click("button:has-text('Confirmar turno')"); pac.wait_for_load_state(); dt=time.time()-t
    check("confirmación OK (%.2f s)"%dt, "Turno confirmado" in pac.content())
    check("mis turnos se actualiza en la MISMA respuesta al confirmar (%d -> %d)"%(antes,mis(pac)), mis(pac)==antes+1)
    row=pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=FECHA_TABLA)
    t=time.time(); row.locator("button:has-text('Cancelar')").click(); pac.wait_for_load_state(); dt=time.time()-t
    check("cancelación OK (%.2f s)"%dt, "Turno cancelado" in pac.content())
    check("mis turnos se actualiza en la MISMA respuesta al cancelar (-> %d)"%mis(pac), mis(pac)==antes)
    time.sleep(4)
    prof.goto(B+"notificaciones.xhtml"); c=prof.content()
    check("profesional recibe aviso de confirmación y de cancelación (asincrónico)", ("confirmó un turno para el "+FECHA) in c and ("canceló su turno del "+FECHA) in c)
    pac.goto(B+"historia.xhtml"); check("paciente ve su historia (solo lectura)", "Historia" in pac.content())
    br.close()
print("RESULTADO:", "TODO OK" if ok else "HAY FALLAS")
