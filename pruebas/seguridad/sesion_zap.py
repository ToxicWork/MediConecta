"""La sesión del paciente de demo para el escaneo autenticado de OWASP ZAP (pruebas/seguridad/zap-plan.yaml).

Entra a la web con Playwright (la cookie de sesión) y a la API (el JWT), y los deja en las variables ZAP_COOKIE y
ZAP_TOKEN del pipeline (GITHUB_ENV), enmascaradas en el log. Fuera del pipeline los guarda en zap-sesion.env, en la
carpeta temporal, para exportarlos a mano; nunca los imprime.
Requisitos: los de las pruebas de punta a punta. Uso:  python pruebas/seguridad/sesion_zap.py
"""
import json, os, ssl, sys, tempfile, urllib.request
from playwright.sync_api import sync_playwright

urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))

B = "https://localhost:8443/mediconecta/"
PACIENTE = "juan.perez@mediconecta.test"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")

with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    pg = br.new_context(ignore_https_errors=True).new_page()
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", PACIENTE)
    pg.fill("#formLogin\\:password", "demo1234")
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    sesion = next((c["value"] for c in pg.context.cookies() if c["name"] == "JSESSIONID"), None)
    en_la_agenda = "agenda.xhtml" in pg.url
    br.close()

pedido = urllib.request.Request(B + "api/v1/auth/login", method="POST", headers={"Content-Type": "application/json"},
                                data=json.dumps({"email": PACIENTE, "password": "demo1234"}).encode())
with urllib.request.urlopen(pedido, timeout=15) as r:
    token = json.loads(r.read())["token"]

if not sesion or not en_la_agenda or not token:
    sys.exit("No se pudo iniciar la sesión del paciente de demo (¿corrió sembrar_usuarios_demo.py?).")

variables = {"ZAP_COOKIE": "JSESSIONID=" + sesion, "ZAP_TOKEN": token}
destino = os.environ.get("GITHUB_ENV") or os.path.join(tempfile.gettempdir(), "zap-sesion.env")
with open(destino, "a", encoding="utf-8") as f:
    for nombre, valor in variables.items():
        if os.environ.get("GITHUB_ENV"):
            print("::add-mask::" + valor)
        f.write("%s=%s\n" % (nombre, valor))
print("Sesión web y JWT del paciente de demo listos para ZAP (%s)." % ("variables del pipeline" if os.environ.get("GITHUB_ENV") else destino))
