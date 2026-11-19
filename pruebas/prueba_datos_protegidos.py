"""Datos protegidos del paciente, sobre la app desplegada: el Decorator PacienteEnmascarado (diseño, sección 4.8) y el
cifrado de columnas con AES-GCM (diseño, sección 8.6; ADR-0022).

  A. Un paciente nuevo se registra con su n.º de afiliado, reserva y paga un turno con la Dra. Ana Lopez, y ella le
     agrega un diagnóstico.
  B. Decorator. La profesional que lo atiende abre la ficha del paciente (desde Historia clínica) y ve el diagnóstico.
     El administrativo abre la ficha del mismo paciente desde Usuarios: ve la identificación y la cobertura, pero no
     el diagnóstico, y la pantalla le explica por qué.
  C. Cifrado. En la base (leída directamente, como la vería quien se lleve una copia) el n.º de afiliado y el
     diagnóstico están cifrados (enc:v1:...) y no aparece el texto en claro; no quedan valores sin cifrar en esas
     columnas y las columnas tienen el ancho nuevo. La aplicación los sigue mostrando bien: el paciente ve su
     diagnóstico en su historia y su n.º de afiliado en Mi perfil.
  D. Validación. Una entrada vacía no se guarda.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados y la clave de
cifrado configurada (wildfly/configurar_cifrado.py), los usuarios de demo, Google Chrome (o Edge con NAVEGADOR=msedge)
y  pip install playwright. Para C hace falta WILDFLY_HOME (lee el datasource de MediConecta, sin imprimirlo, y usa el
driver de PostgreSQL de WildFly) y Java; sin WILDFLY_HOME, C se omite.
Uso:  python pruebas/prueba_datos_protegidos.py
Deja en la base un paciente de prueba por corrida (protegido.NNNNN@mediconecta.test, OSDE) con un turno confirmado a
33-39 días de hoy con la Dra. Ana Lopez y un diagnóstico.
"""
import glob, json, os, random, re, ssl, subprocess, sys, urllib.request
from datetime import date, timedelta
from playwright.sync_api import sync_playwright

urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))
try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "https://localhost:8443/mediconecta/"
EXT = "http://localhost:8080/externos/"
PROFESIONAL = "ana.lopez@mediconecta.test"
ADMINISTRATIVO = "carla.admin@mediconecta.test"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
WILDFLY_HOME = os.environ.get("WILDFLY_HOME")
VERIFICAR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "VerificarCifrado.java")
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def consola(**modos):
    pedido = urllib.request.Request(EXT + "api/consola/estado", method="PUT", data=json.dumps(modos).encode(),
                                    headers={"Content-Type": "application/json"})
    urllib.request.urlopen(pedido, timeout=10)


def datasource_de_wildfly():
    """URL, usuario y contraseña del datasource de MediConecta, leídos de la configuración de WildFly (no se imprimen)."""
    try:
        with open(os.path.join(WILDFLY_HOME, "standalone", "configuration", "standalone-full.xml"), encoding="utf-8") as xml:
            configuracion = xml.read()
        bloque = configuracion[configuracion.index('jndi-name="java:/mediconectaDS"'):]
    except (OSError, ValueError, TypeError):
        return None
    url = re.search(r"<connection-url>([^<]+)</connection-url>", bloque)
    credenciales = re.search(r'<security user-name="([^"]+)" password="([^"]+)"', bloque)
    drivers = glob.glob(os.path.join(WILDFLY_HOME, "modules", "org", "postgresql", "main", "*.jar"))
    if not (url and credenciales and drivers):
        return None
    return drivers[0], {"MC_URL": url.group(1), "MC_USUARIO": credenciales.group(1), "MC_CLAVE": credenciales.group(2)}


def pagina(br):
    return br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()


def login(br, email, clave="demo1234"):
    pg = pagina(br)
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", clave)
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg


def mensajes(pg):
    return pg.locator(".ui-growl-message").all_inner_texts()


def publicar_libre(prof):
    """Un horario libre a 33-39 días de hoy; si ya existe uno igual de otra corrida (D-23), elige otro."""
    for _ in range(15):
        dia = (date.today() + timedelta(days=random.randint(33, 39))).strftime("%d/%m/%Y")
        fecha = "%s %02d:%02d" % (dia, random.randint(7, 19), random.randint(0, 11) * 5)
        prof.goto(B + "agenda.xhtml")
        prof.fill("#formPublicar\\:fecha_input", fecha)
        prof.keyboard.press("Escape")
        with prof.expect_navigation():
            prof.locator("#formPublicar button:has-text('Publicar')").evaluate("b => b.click()")
        if any("Disponibilidad publicada" in m for m in mensajes(prof)):
            return fecha
    raise AssertionError("no se encontró un horario libre")


def reservar_y_pagar(pac, fecha):
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    dia, hora = fecha.split(" ")
    with pac.expect_navigation():
        pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Reservar')").first.click()
    if pac.locator("#formConfirmacion\\:btnConfirmar").count():
        pac.fill("#formConfirmacion\\:titular", "Paciente Protegido")
        pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
        pac.fill("#formConfirmacion\\:vencimiento", "")
        pac.fill("#formConfirmacion\\:vencimiento", "12/29")
        pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
        pac.click("#formConfirmacion\\:btnConfirmar")
        with pac.expect_navigation():
            pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    else:
        with pac.expect_navigation():
            pac.click("#formConfirmacion\\:btnConfirmarSinPago")
    return " ".join(mensajes(pac))


def abrir_historia(prof, nombre):
    prof.goto(B + "historia.xhtml")
    opcion = next(o for o in prof.locator("#formSeleccion\\:paciente option").all_inner_texts() if nombre in o)
    prof.select_option("#formSeleccion\\:paciente", label=opcion)
    with prof.expect_navigation():
        prof.click("#formSeleccion button:has-text('Ver historia')")


def ver_ficha(pg, enlace):
    with pg.expect_response(lambda r: r.request.method == "POST"):
        enlace.click()
    dialogo = pg.locator("#dialogoFicha")
    dialogo.wait_for(state="visible", timeout=10000)
    texto = dialogo.inner_text()
    pg.keyboard.press("Escape")
    return texto


with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR")
    sufijo = random.randint(10000, 99999)
    email, nombre = "protegido.%d@mediconecta.test" % sufijo, "Paciente Protegido %d" % sufijo
    afiliado = "AF-%d-77" % sufijo
    diagnostico = "Diagnóstico reservado %d: hipertensión leve, control en 30 días" % sufijo

    # ---- A. Paciente con turno confirmado y un diagnóstico ---------------------------------------------
    print("-- A. El paciente se registra con su n.º de afiliado y la profesional le agrega un diagnóstico")
    nuevo = pagina(br)
    nuevo.goto(B + "registro.xhtml")
    for campo, valor in (("nombre", nombre), ("email", email), ("password", "demo1234"), ("repeticion", "demo1234"),
                         ("obraSocial", "OSDE 210"), ("nroAfiliado", afiliado)):
        nuevo.fill("#formRegistro\\:" + campo, valor)
    with nuevo.expect_navigation():
        nuevo.click("#formRegistro button:has-text('Registrarme')")
    nuevo.context.close()
    prof = login(br, PROFESIONAL)
    pac = login(br, email)
    check("A. el paciente confirma un turno con la Dra. Ana Lopez", "Turno confirmado" in reservar_y_pagar(pac, publicar_libre(prof)))
    abrir_historia(prof, nombre)
    prof.fill("#formHistoria\\:descripcion", diagnostico)
    with prof.expect_navigation():
        prof.click("#formHistoria button:has-text('Guardar')")
    check("A. la profesional guarda el diagnóstico y aparece en la línea de tiempo", diagnostico in prof.inner_text("#formHistoria"))

    # ---- D. Validación ------------------------------------------------------------------------------------
    print("-- D. Una entrada vacía no se guarda")
    prof.fill("#formHistoria\\:descripcion", "   ")
    with prof.expect_navigation():
        prof.click("#formHistoria button:has-text('Guardar')")
    check("D. con la descripción vacía lo pide y no agrega nada (%s)" % mensajes(prof),
          any("Escribí el diagnóstico o la receta" in m for m in mensajes(prof))
          and "1 entrada(s)" in prof.inner_text("#formHistoria"))

    # ---- B. Decorator: qué ve cada uno en la ficha -----------------------------------------------------------
    print("-- B. Ficha del paciente (Decorator PacienteEnmascarado)")
    abrir_historia(prof, nombre)
    ficha_prof = ver_ficha(prof, prof.locator("#formHistoria\\:verFicha"))
    check("B. la profesional que lo atiende ve el diagnóstico en la ficha", diagnostico in ficha_prof)
    check("B. y la identificación y la cobertura", nombre in ficha_prof and afiliado in ficha_prof and "OSDE 210" in ficha_prof)

    prof.goto(B + "agenda.xhtml")
    turno = prof.locator("#formTurnosProfesional\:tablaConfirmadosProfesional tr", has_text=nombre)
    check("B. el turno del paciente está en los próximos turnos de la Agenda", turno.count() >= 1)
    if turno.count():
        check("B. y desde ahí la profesional también ve el diagnóstico en la ficha",
              diagnostico in ver_ficha(prof, turno.first.locator("a:has-text('Ver ficha')")))

    adm = login(br, ADMINISTRATIVO)
    adm.goto(B + "usuarios.xhtml")
    fila = adm.locator("#formUsuarios\\:tablaUsuarios tr", has_text=email)
    ficha_adm = ver_ficha(adm, fila.locator("a:has-text('Ver ficha')"))
    check("B. el administrativo ve la identificación y la cobertura del paciente",
          nombre in ficha_adm and afiliado in ficha_adm and "OSDE 210" in ficha_adm)
    check("B. pero no el diagnóstico", diagnostico not in ficha_adm and "hipertensión" not in ficha_adm.lower())
    check("B. y la pantalla le explica por qué", "sólo los ve el profesional que atiende al paciente" in ficha_adm)
    check("B. un profesional o un administrativo no tiene \"Ver ficha\" en su propia fila de Usuarios",
          adm.locator("#formUsuarios\\:tablaUsuarios tr", has_text=PROFESIONAL).locator("a:has-text('Ver ficha')").count() == 0)

    # ---- C. Cifrado en la base -----------------------------------------------------------------------------
    print("-- C. Cifrado de columnas con AES-GCM")
    pac.goto(B + "historia.xhtml")
    check("C. el paciente ve su diagnóstico en claro en su historia", diagnostico in pac.inner_text("body"))
    pac.goto(B + "perfil.xhtml")
    check("C. y su n.º de afiliado en Mi perfil", afiliado in pac.inner_text("body"))
    datasource = datasource_de_wildfly()
    if datasource is None:
        print("-- C (base): se omite; necesita WILDFLY_HOME con el datasource de MediConecta configurado")
    else:
        driver, credenciales = datasource
        salida = subprocess.run(["java", "-cp", driver, VERIFICAR, email, afiliado, diagnostico],
                                env={**os.environ, **credenciales}, capture_output=True, text=True, timeout=120)
        base = dict(linea.split("=", 1) for linea in salida.stdout.split() if "=" in linea)
        if salida.returncode != 0:
            print("   la verificación no corrió: " + (salida.stderr.strip().splitlines() or ["sin detalle"])[-1][:200])
        check("C. en la base el n.º de afiliado está cifrado (enc:v1:...) y no aparece en claro",
              base.get("afiliado_cifrado") == "true" and base.get("afiliado_en_claro") == "false")
        check("C. el diagnóstico está cifrado y el texto no aparece en la base (%s de %s entradas cifradas)"
              % (base.get("entradas_cifradas"), base.get("entradas")),
              base.get("entradas") == "1" and base.get("entradas_cifradas") == "1" and base.get("entrada_en_claro") == "false")
        check("C. no quedan valores sin cifrar en esas columnas (%s)" % base.get("pendientes"), base.get("pendientes") == "0")
        check("C. las columnas tienen el ancho del dato cifrado (%s y %s)" % (base.get("largo_afiliado"), base.get("largo_descripcion")),
              int(base.get("largo_afiliado", 0)) >= 256 and int(base.get("largo_descripcion", 0)) >= 8100)

    for pg in (prof, pac, adm):
        pg.context.close()
    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
