"""Accesibilidad (WCAG 2.2 AA): cada pantalla de cada rol a 360, 768 y 1280 px.

  1. axe-core en las 19 pantallas de los tres roles (y las dos sin sesión) a los tres anchos: ninguna violación
     crítica de las reglas WCAG 2.0, 2.1 y 2.2 de niveles A y AA. Las graves (serious) se listan para corregirlas.
  2. Opcional (fuera del pipeline): Lighthouse en las pantallas principales a los tres anchos, con puntaje de
     accesibilidad de 90 o más.

Deja el detalle en la carpeta de RESULTADOS_ACCESIBILIDAD (por defecto, mediconecta-accesibilidad en la carpeta
temporal): axe.json con cada violación y, por cada corrida de Lighthouse, su reporte HTML y JSON.

Requisitos: los de prueba_responsive.py, más
  AXE_JS       la ruta de axe.min.js (del paquete axe-core 4.14.0; el pipeline lo baja con su sha512);
  LIGHTHOUSE   el comando de Lighthouse (por ejemplo "npx --yes lighthouse@13.5.0"); sin ella, se omite el paso 2.
Uso:  python pruebas/prueba_accesibilidad.py
No modifica datos.
"""
import json, os, shlex, subprocess, sys, tempfile
from playwright.sync_api import sync_playwright

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "https://localhost:8443/mediconecta/"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
AXE_JS = os.environ.get("AXE_JS", "")
LIGHTHOUSE = os.environ.get("LIGHTHOUSE", "")
RESULTADOS = os.environ.get("RESULTADOS_ACCESIBILIDAD") or os.path.join(tempfile.gettempdir(), "mediconecta-accesibilidad")
ANCHOS = {360: (740, True), 768: (1024, False), 1280: (800, False)}
REGLAS = ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa", "wcag22aa"]
PANTALLAS = {
    None: ["login.xhtml", "registro.xhtml"],
    ("paciente", "juan.perez@mediconecta.test"): ["agenda.xhtml", "historia.xhtml", "telemedicina.xhtml",
                                                  "notificaciones.xhtml", "pagos.xhtml", "monitor.xhtml", "perfil.xhtml"],
    ("profesional", "ana.lopez@mediconecta.test"): ["agenda.xhtml", "historia.xhtml", "telemedicina.xhtml",
                                                    "notificaciones.xhtml", "monitor.xhtml", "perfil.xhtml"],
    ("administrativo", "carla.admin@mediconecta.test"): ["pagos.xhtml", "turnos.xhtml", "usuarios.xhtml", "perfil.xhtml"],
}
# Las que se miden también con Lighthouse: la entrada y la pantalla principal de cada rol.
CON_LIGHTHOUSE = {"login.xhtml", "registro.xhtml", "agenda.xhtml", "historia.xhtml", "pagos.xhtml", "usuarios.xhtml"}
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def lighthouse(url, cookies, ancho, destino):
    """Corre Lighthouse (sólo accesibilidad) con la sesión del rol; devuelve el puntaje de 0 a 100 o None."""
    alto, celular = ANCHOS[ancho]
    cabeceras = destino + "-cabeceras.json"
    with open(cabeceras, "w", encoding="utf-8") as f:
        json.dump({"Cookie": "; ".join("%s=%s" % (c["name"], c["value"]) for c in cookies)} if cookies else {}, f)
    comando = shlex.split(LIGHTHOUSE) + [
        url, "--only-categories=accessibility", "--output=json", "--output=html", "--output-path=" + destino,
        "--quiet", "--throttling-method=provided", "--disable-full-page-screenshot", "--extra-headers=" + cabeceras,
        "--form-factor=" + ("mobile" if celular else "desktop"), "--screenEmulation.width=%d" % ancho,
        "--screenEmulation.height=%d" % alto, "--screenEmulation.deviceScaleFactor=%d" % (2 if celular else 1),
        "--screenEmulation.mobile=%s" % str(celular).lower(),
        "--chrome-flags=--headless=new --ignore-certificate-errors --no-sandbox"]
    try:
        subprocess.run(comando, capture_output=True, text=True, timeout=180, stdin=subprocess.DEVNULL, check=True)
        os.remove(cabeceras)
        with open(destino + ".report.json", encoding="utf-8") as f:
            reporte = json.load(f)
    except (subprocess.SubprocessError, OSError, ValueError) as e:
        print("     Lighthouse no pudo medir %s: %s" % (url, str(e)[:200]))
        return None, []
    fallidas = [a["title"] for a in reporte["audits"].values()
                if a.get("score") == 0 and a.get("scoreDisplayMode") == "binary"]
    puntaje = reporte["categories"]["accessibility"]["score"]
    return (None if puntaje is None else round(puntaje * 100)), fallidas


if not AXE_JS or not os.path.exists(AXE_JS):
    sys.exit("Definí AXE_JS con la ruta de axe.min.js (npm install axe-core@4.14.0).")
os.makedirs(RESULTADOS, exist_ok=True)
axe = open(AXE_JS, encoding="utf-8").read()
detalle, graves = [], 0
with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    for rol, pantallas in PANTALLAS.items():
        # bypass_csp: la CSP de la aplicación (H01) no deja ejecutar scripts sin su nonce, y axe se inyecta en la
        # página. Sólo afecta a este navegador de prueba; la aplicación no cambia.
        contexto = br.new_context(ignore_https_errors=True, bypass_csp=True, viewport={"width": 1280, "height": 800})
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
            for ancho, (alto, _) in ANCHOS.items():
                pg.set_viewport_size({"width": ancho, "height": alto})
                pg.goto(B + pantalla)
                pg.wait_for_load_state("networkidle")
                if pantalla not in pg.url:
                    check("%s · %s a %d px: la pantalla abre (fue a %s)" % (nombre_rol, pantalla, ancho, pg.url.split("/")[-1]), False)
                    continue
                pg.add_script_tag(content=axe)
                resultado = pg.evaluate("reglas => axe.run(document, {runOnly: {type: 'tag', values: reglas}})", REGLAS)
                violaciones = [{"regla": v["id"], "impacto": v["impact"], "ayuda": v["help"], "nodos": len(v["nodes"]),
                                "ejemplo": v["nodes"][0]["target"] if v["nodes"] else []} for v in resultado["violations"]]
                criticas = [v for v in violaciones if v["impacto"] == "critical"]
                serias = [v for v in violaciones if v["impacto"] == "serious"]
                graves += len(serias)
                detalle.append({"rol": nombre_rol, "pantalla": pantalla, "ancho": ancho, "violaciones": violaciones})
                check("%s · %s a %d px: sin violaciones críticas de WCAG 2.2 AA%s%s" % (
                    nombre_rol, pantalla, ancho,
                    "" if not criticas else " (%s)" % ", ".join("%s en %d" % (v["regla"], v["nodos"]) for v in criticas),
                    "" if not serias else "; graves: %s" % ", ".join(v["regla"] for v in serias)), not criticas)
        if LIGHTHOUSE:
            cookies = contexto.cookies()
            for pantalla in (p for p in pantallas if p in CON_LIGHTHOUSE):
                for ancho in ANCHOS:
                    destino = os.path.join(RESULTADOS, "lighthouse-%s-%s-%d" % (nombre_rol.replace(" ", "-"), pantalla[:-6], ancho))
                    puntaje, fallidas = lighthouse(B + pantalla, cookies, ancho, destino)
                    check("%s · %s a %d px: Lighthouse da %s de accesibilidad (mínimo 90)%s" % (
                        nombre_rol, pantalla, ancho, "sin puntaje" if puntaje is None else puntaje,
                        "" if not fallidas else "; no cumple: " + "; ".join(fallidas[:3])),
                          puntaje is not None and puntaje >= 90)
        contexto.close()
    br.close()

# Las violaciones graves (serious), agrupadas por regla, como AVISO: el pipeline las muestra como advertencias.
por_regla = {}
for d in detalle:
    for v in d["violaciones"]:
        if v["impacto"] == "serious":
            por_regla.setdefault((v["regla"], v["ayuda"]), set()).add("%s/%s" % (d["rol"], d["pantalla"][:-6]))
for (regla, ayuda), donde in sorted(por_regla.items()):
    print("AVISO grave: %s (%s) en %d pantallas: %s" % (regla, ayuda, len(donde), ", ".join(sorted(donde))[:300]))
with open(os.path.join(RESULTADOS, "axe.json"), "w", encoding="utf-8") as f:
    json.dump(detalle, f, ensure_ascii=False, indent=1)
print("\n%d combinaciones con axe; violaciones graves (serious) en total: %d. Detalle en %s"
      % (len(detalle), graves, RESULTADOS))
if not LIGHTHOUSE:
    print("Lighthouse: definí LIGHTHOUSE para medir el puntaje (se omitió).")
print("RESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
