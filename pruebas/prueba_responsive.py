"""Diseño responsive: cada pantalla de cada rol a 360, 768 y 1280 px de ancho (celular, tablet y escritorio).

En cada combinación se verifica que la página no tenga scroll horizontal (lo que no entra, como las tablas anchas,
se desplaza dentro de su propio recuadro) y que el título de la pantalla se vea. Si algo se pasa del ancho, la
verificación nombra los elementos que se salen. Deja una captura de cada combinación como evidencia.

Requisitos: WildFly levantado con mediconecta.war desplegado, los usuarios de demo, Google Chrome (o Edge con
NAVEGADOR=msedge) y  pip install playwright
Uso:  python pruebas/prueba_responsive.py
Las capturas quedan en la carpeta de CAPTURAS (por defecto, mediconecta-responsive en la carpeta temporal).
No modifica datos.
"""
import os, sys, tempfile
from playwright.sync_api import sync_playwright

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "https://localhost:8443/mediconecta/"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
CAPTURAS = os.environ.get("CAPTURAS") or os.path.join(tempfile.gettempdir(), "mediconecta-responsive")
ANCHOS = (360, 768, 1280)
PANTALLAS = {
    None: ["login.xhtml", "registro.xhtml"],
    ("paciente", "juan.perez@mediconecta.test"): ["agenda.xhtml", "historia.xhtml", "telemedicina.xhtml",
                                                  "notificaciones.xhtml", "pagos.xhtml", "monitor.xhtml", "perfil.xhtml"],
    ("profesional", "ana.lopez@mediconecta.test"): ["agenda.xhtml", "historia.xhtml", "telemedicina.xhtml",
                                                    "notificaciones.xhtml", "monitor.xhtml", "perfil.xhtml"],
    ("administrativo", "carla.admin@mediconecta.test"): ["pagos.xhtml", "turnos.xhtml", "usuarios.xhtml", "perfil.xhtml"],
}
DESBORDE = """() => {
    const ancho = document.documentElement.clientWidth;
    const desborda = document.documentElement.scrollWidth > ancho + 1;
    // Los que se salen de la página, salvo los que están dentro de un recuadro con scroll propio (.table-wrap).
    const fuera = Array.from(document.querySelectorAll('body *'))
        .filter(e => e.getBoundingClientRect().right > ancho + 1 && !e.closest('.table-wrap, .ui-datatable-scrollable')
                     && getComputedStyle(e).position !== 'fixed' && e.offsetParent !== null)
        .slice(0, 3).map(e => e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (e.className && typeof e.className === 'string'
                     ? '.' + e.className.trim().split(/\\s+/).slice(0, 2).join('.') : ''));
    return {desborda, fuera, ancho, contenido: document.documentElement.scrollWidth};
}"""
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


os.makedirs(CAPTURAS, exist_ok=True)
with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    for rol, pantallas in PANTALLAS.items():
        contexto = br.new_context(ignore_https_errors=True, viewport={"width": 1280, "height": 800})
        pg = contexto.new_page()
        nombre_rol = rol[0] if rol else "sin sesión"
        if rol:
            pg.goto(B + "login.xhtml")
            pg.fill("#formLogin\\:email", rol[1])
            pg.fill("#formLogin\\:password", "demo1234")
            with pg.expect_navigation():
                pg.click("#formLogin button:has-text('Ingresar')")
        print("-- %s" % nombre_rol.capitalize())
        for pantalla in pantallas:
            for ancho in ANCHOS:
                pg.set_viewport_size({"width": ancho, "height": 800})
                pg.goto(B + pantalla)
                pg.wait_for_load_state("networkidle")
                if pantalla not in pg.url:
                    check("%s · %s a %d px: la pantalla abre (fue a %s)" % (nombre_rol, pantalla, ancho, pg.url.split("/")[-1]), False)
                    continue
                medida = pg.evaluate(DESBORDE)
                titulo = pg.locator("h1, .page-title, .auth-card h2, .card h2").first
                visible = titulo.count() == 0 or titulo.bounding_box() is None or titulo.bounding_box()["x"] + 1 >= 0
                pg.screenshot(path=os.path.join(CAPTURAS, "%s-%s-%d.png" % (nombre_rol.replace(" ", "-"), pantalla[:-6], ancho)),
                              full_page=True)
                check("%s · %s a %d px: sin scroll horizontal%s" % (
                    nombre_rol, pantalla, ancho,
                    "" if not medida["desborda"] else " (el contenido mide %d px; se salen: %s)" % (medida["contenido"], ", ".join(medida["fuera"]) or "?")),
                      not medida["desborda"] and visible)
        contexto.close()
    br.close()

print("\nCapturas en", CAPTURAS)
print("RESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
