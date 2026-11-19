"""Inyección SQL, XSS y de expresiones (EL) en todos los campos de texto, no sólo en el login.

Cada campo que se guarda y se vuelve a mostrar recibe a la vez un script, una etiqueta con onerror, una expresión
EL de JSF y SQL. En cada pantalla donde aparece se verifica que se vea literal (escapado), que no se ejecute
nada (ni la marca global, ni diálogos, ni elementos inyectados), que la expresión no se evalúe y que no haya
errores 500 ni rastros de la base.

  J01. Registro: nombre, obra social y n.º de afiliado del paciente.
  J02. Mi perfil del paciente, el listado de Usuarios del administrativo y su pantalla Pagos.
  J03. Agenda y selector de pacientes de la profesional (el nombre del paciente).
  J04. Historia clínica: la profesional escribe la entrada, la ven ella y el paciente.
  J05. Titular de la tarjeta, la fecha al publicar un horario y el login (lo que se reescribe en el formulario).
  J06. API: SQL en el id del turno y en los datos del login.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados, Google
Chrome (o Edge con NAVEGADOR=msedge) y  pip install playwright
Uso:  python pruebas/prueba_inyeccion.py
Deja en la base un paciente de prueba por corrida (inyeccion.NNNNN@mediconecta.test, con los datos de ataque como
texto) con un turno confirmado y una entrada de historia clínica con la Dra. Ana Lopez.
"""
import json, os, random, re, ssl, sys, urllib.error, urllib.request
from datetime import date, timedelta
from playwright.sync_api import sync_playwright

# MediConecta corre por HTTPS con el certificado autofirmado que WildFly genera para localhost: se acepta sin validar.
urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))
try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "https://localhost:8443/mediconecta/"
EXT = "http://localhost:8080/externos/"
PROFESIONAL, ADMINISTRATIVO = "ana.lopez@mediconecta.test", "carla.admin@mediconecta.test"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")

XSS = '<script>window.__xss=1</script><img src=x onerror="window.__xss=1">'
EL = "#{7*7}"
SQL = "' OR '1'='1'; DROP TABLE turno;--"
RASTROS_DE_BASE = re.compile(r"PSQLException|SQLState|SQL state|syntax error at or near|org\.hibernate|"
                             r"jakarta\.persistence|java\.lang\.|\bat [a-z]+\.[a-z]+\.[A-Za-z]+\(", re.I)
ok = True
alertas = []


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def dia_desde_hoy(dias):
    return (date.today() + timedelta(days=dias)).strftime("%d/%m/%Y")


def consola(**modos):
    pedido = urllib.request.Request(EXT + "api/consola/estado", method="PUT", data=json.dumps(modos).encode(),
                                    headers={"Content-Type": "application/json"})
    urllib.request.urlopen(pedido, timeout=10)


def api(ruta, metodo="GET", cuerpo=None, token=None):
    pedido = urllib.request.Request(B + "api/v1/" + ruta, method=metodo,
                                    data=None if cuerpo is None else json.dumps(cuerpo).encode())
    pedido.add_header("Content-Type", "application/json")
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(pedido, timeout=15) as respuesta:
            return respuesta.status, respuesta.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()


def pagina(br):
    pg = br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()
    pg.on("dialog", lambda dialogo: (alertas.append(dialogo.message), dialogo.dismiss()))
    return pg


def login(br, email, inicio="agenda.xhtml"):
    pg = pagina(br)
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", "demo1234")
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg


def mensaje(pg):
    return " ".join(pg.locator(".ui-growl-message").all_inner_texts())


def nada_se_ejecuto(pg):
    """Ni la marca global, ni diálogos, ni elementos inyectados: un script que sea la carga de ataque, la imagen o un
    atributo on* con ella. (Que la carga aparezca dentro de un string de JavaScript, escapada, no es inyección:
    PrimeFaces arma así, por ejemplo, el mensaje del diálogo de confirmación).
    """
    inyectado = pg.evaluate("""() => window.__xss !== undefined
        || Array.from(document.scripts).some(s => s.textContent.trim() === 'window.__xss=1')
        || document.querySelectorAll('img[src="x"]').length > 0
        || Array.from(document.querySelectorAll('*')).some(e => Array.from(e.attributes)
               .some(a => a.name.startsWith('on') && a.value.includes('__xss')))""")
    return not inyectado and not alertas


def literal(pg, texto):
    """El texto se ve tal cual (escapado): con las etiquetas y la expresión sin interpretar."""
    return texto in pg.inner_text("body")


def sin_rastros(pg):
    return not RASTROS_DE_BASE.search(pg.inner_text("body"))


with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR")
    sufijo = random.randint(10000, 99999)
    email = "inyeccion.%d@mediconecta.test" % sufijo
    nombre = "Paciente %d %s %s %s" % (sufijo, XSS, EL, SQL)
    obra_social = "OS %s %s" % (EL, SQL)
    afiliado = "%d%s" % (sufijo, SQL)

    # ---- J01. Registro --------------------------------------------------------------------------------
    print("-- J01. Registro con los datos de ataque")
    registro = pagina(br)
    registro.goto(B + "registro.xhtml")
    for campo, valor in (("nombre", nombre), ("email", email), ("password", "demo1234"), ("repeticion", "demo1234"),
                         ("obraSocial", obra_social), ("nroAfiliado", afiliado)):
        registro.fill("#formRegistro\\:" + campo, valor)
    with registro.expect_navigation():
        registro.click("#formRegistro button:has-text('Registrarme')")
    check("J01. el registro con un script, una expresión EL y SQL en nombre, obra social y n.º de afiliado se acepta "
          "como texto (%s)" % mensaje(registro)[:60], "login.xhtml" in registro.url and "Registro exitoso" in mensaje(registro))
    check("J01. sin rastros de la base ni ejecución en la respuesta", sin_rastros(registro) and nada_se_ejecuto(registro))
    registro.context.close()

    # ---- J02. Donde se muestran esos datos -----------------------------------------------------------
    print("-- J02. Mi perfil, Usuarios y Pagos")
    pac = login(br, email)
    pac.goto(B + "perfil.xhtml")
    check("J02. Mi perfil muestra el nombre literal, con el script y la expresión sin interpretar",
          literal(pac, XSS) and literal(pac, EL))
    check("J02. y en Mi perfil no se ejecutó nada", nada_se_ejecuto(pac))
    adm = login(br, ADMINISTRATIVO, "pagos.xhtml")
    adm.goto(B + "usuarios.xhtml")
    fila = adm.locator("[id$='tablaUsuarios'] tr", has_text=email)
    check("J02. el listado de Usuarios del administrativo muestra al paciente con su nombre literal",
          fila.count() == 1 and XSS in fila.inner_text() and EL in fila.inner_text())
    check("J02. y en Usuarios no se ejecutó nada", nada_se_ejecuto(adm))

    # ---- J03. La profesional ve al paciente ------------------------------------------------------------
    print("-- J03. Agenda y historia de la profesional")
    prof = login(br, PROFESIONAL)
    for _ in range(15):  # un horario libre (D-23 no deja repetir uno de otra corrida)
        fecha = "%s %02d:%02d" % (dia_desde_hoy(random.randint(49, 55)), random.randint(7, 19), random.randint(0, 59))
        prof.goto(B + "agenda.xhtml")
        prof.fill("#formPublicar\\:fecha_input", fecha)
        prof.keyboard.press("Escape")
        with prof.expect_navigation():
            prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
        if "Disponibilidad publicada" in mensaje(prof):
            break
    dia, hora = fecha.split(" ")
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    with pac.expect_navigation():
        pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Reservar')").first.click()
    titular = "Titular %s" % XSS[:40]
    if pac.locator("#formConfirmacion\\:titular").count():
        pac.fill("#formConfirmacion\\:titular", titular)
        pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
        pac.fill("#formConfirmacion\\:vencimiento", "")
        pac.fill("#formConfirmacion\\:vencimiento", "12/29")
        pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
    pac.click("#formConfirmacion\\:btnConfirmar")
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    check("J05. el titular de la tarjeta con un script se procesa como texto: el turno se confirma (%s)" % fecha,
          "Turno confirmado" in pac.content() and sin_rastros(pac) and nada_se_ejecuto(pac))
    prof.goto(B + "agenda.xhtml")
    check("J03. la Agenda de la profesional muestra el turno con el nombre del paciente literal",
          prof.locator("tr", has_text=str(sufijo)).count() > 0 and literal(prof, XSS))
    check("J03. y en su Agenda no se ejecutó nada", nada_se_ejecuto(prof))
    # El nombre viaja dentro de JavaScript (la configuración del diálogo de confirmación): se abre y se mira.
    prof.locator("tr", has_text=str(sufijo)).locator("button:has-text('Cancelar')").first.click()
    dialogo = prof.locator(".ui-confirm-dialog:visible")
    dialogo.wait_for()
    check("J03. el diálogo «¿Cancelar el turno de...?» muestra el nombre literal y no ejecuta nada",
          XSS in dialogo.inner_text() and EL in dialogo.inner_text() and nada_se_ejecuto(prof))
    dialogo.locator("button.ui-confirmdialog-no").click()
    prof.goto(B + "historia.xhtml")
    opciones = [o for o in prof.locator("#formSeleccion\\:paciente option").all_inner_texts() if str(sufijo) in o]
    check("J03. el selector de pacientes de la historia clínica lo lista con el nombre literal",
          len(opciones) == 1 and XSS in opciones[0])

    # ---- J04. Historia clínica ---------------------------------------------------------------------------
    print("-- J04. Historia clínica")
    prof.select_option("#formSeleccion\\:paciente", label=opciones[0])
    with prof.expect_navigation():
        prof.click("#formSeleccion button:has-text('Ver historia')")
    entrada = "Diagnóstico %d %s %s %s" % (sufijo, XSS, EL, SQL)
    prof.fill("#formHistoria\\:descripcion", entrada)
    with prof.expect_navigation():
        prof.click("#formHistoria button:has-text('Guardar')")
    check("J04. la profesional guarda una entrada con script, expresión EL y SQL, y la ve literal",
          literal(prof, "Diagnóstico %d" % sufijo) and literal(prof, XSS) and literal(prof, EL) and literal(prof, SQL))
    check("J04. y en su pantalla no se ejecutó nada", nada_se_ejecuto(prof))
    pac.goto(B + "historia.xhtml")
    check("J04. el paciente ve la entrada literal en su historia (XSS almacenado)",
          literal(pac, "Diagnóstico %d" % sufijo) and literal(pac, XSS) and literal(pac, EL))
    check("J04. y en la historia del paciente no se ejecutó nada", nada_se_ejecuto(pac))
    adm.goto(B + "pagos.xhtml")
    check("J02. la pantalla Pagos del administrativo muestra el cobro con el nombre del paciente literal",
          adm.locator("[id$='tablaPagos'] tr", has_text=str(sufijo)).count() > 0 and literal(adm, XSS))
    check("J02. y en Pagos no se ejecutó nada", nada_se_ejecuto(adm))

    # ---- J05. Fecha al publicar y login ---------------------------------------------------------------------
    print("-- J05. Fecha al publicar un horario y login")
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", "01/01/2027 10:00" + SQL)
    prof.keyboard.press("Escape")
    with prof.expect_navigation():
        prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
    check("J05. una fecha con SQL al publicar se rechaza como fecha inválida, sin rastros de la base (%s)" % mensaje(prof)[:70],
          "Disponibilidad publicada" not in mensaje(prof) and sin_rastros(prof) and nada_se_ejecuto(prof))
    ataque = pagina(br)
    ataque.goto(B + "login.xhtml")
    ataque.fill("#formLogin\\:email", '"><script>window.__xss=1</script>@x.test')
    ataque.fill("#formLogin\\:password", SQL)
    with ataque.expect_navigation():
        ataque.click("#formLogin button:has-text('Ingresar')")
    check("J05. el login con un script en el email y SQL en la contraseña responde el error genérico, sin ejecutar nada",
          "Credenciales inválidas" in mensaje(ataque) and nada_se_ejecuto(ataque) and sin_rastros(ataque))
    ataque.context.close()

    # ---- J06. API ------------------------------------------------------------------------------------------------
    print("-- J06. API")
    token = json.loads(api("auth/login", "POST", {"email": email, "password": "demo1234"})[1])["token"]
    for ruta in ("turnos/1%20OR%201=1", "turnos/1;DROP%20TABLE%20turno", "turnos/%27"):
        estado, cuerpo = api(ruta, token=token)
        check("J06. GET /api/v1/%s responde 4xx sin rastros de la base (%d)" % (urllib.request.unquote(ruta), estado),
              400 <= estado < 500 and not RASTROS_DE_BASE.search(cuerpo.replace("problem+json", "")))
    estado, cuerpo = api("auth/login", "POST", {"email": XSS + "@x.test", "password": SQL})
    check("J06. el login de la API con un script en el email y SQL en la contraseña responde 401 sin reflejar el script (%d)" % estado,
          estado == 401 and "<script>" not in cuerpo and not RASTROS_DE_BASE.search(cuerpo))

    check("ninguna página abrió un diálogo (alert, confirm o prompt) en toda la prueba (%s)" % alertas, not alertas)
    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
