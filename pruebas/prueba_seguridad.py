"""Pruebas de seguridad del diseño (tarjetas SEC-* del tablero) sobre la app desplegada.

  W01. Bloqueo tras 5 intentos fallidos: la cuenta rechaza incluso la contraseña correcta, con 429 y Retry-After
       (web y API, Eje D); un email que no existe recibe exactamente lo mismo, así el 429 no revela qué cuentas
       hay; un ingreso correcto pone el contador en cero; el administrativo la desbloquea desde Usuarios.
  C01. Errores de la API en Problem Details (RFC 9457): 400, 401, 403, 404, 405, 415 y 422 con
       application/problem+json, type, title, detail e instance, sin trazas ni nombres internos.
  W04. Cookie de sesión con HttpOnly, Secure y SameSite=Lax; toda la app por HTTPS (http redirige a https, también
       la API) y la cookie no es legible desde JavaScript.
  W06. Login resistente a inyección SQL: los ataques típicos en el email y la contraseña (web y API) reciben el
       rechazo genérico, sin error 500 ni detalles de la base; en el registro, un nombre "peligroso" se guarda
       como texto literal; la base sigue intacta.
  D01. Historia clínica sólo con turno confirmado: sin turno el profesional no ve al paciente; con un turno
       confirmado abre su historia y registra un diagnóstico; si el turno se cancela con la historia todavía abierta
       en pantalla, el servidor rechaza la nueva entrada.
  I02. Dead Letter Channel: con la pasarela de pagos caída, Facturación no puede reintegrar una cancelación; el
       broker reintenta 3 veces y deja el mensaje en jms.queue.DLQ (no se pierde). Con la pasarela de nuevo en
       línea, se mueve el mensaje de vuelta a Facturación y el reintegro se hace.
  D02. Logs sin datos sensibles: en todo lo que esta corrida escribió en el log del servidor no aparece el email
       completo, el nombre, la contraseña ni el diagnóstico de los usuarios de prueba; los emails se ven
       enmascarados (se***@mediconecta.test), también en el alta de un usuario por el administrativo.
       D-34: los rechazos esperados de la API (401, 403, 404, 429) no dejan errores con traza en el log.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados, Google
Chrome (o Edge con NAVEGADOR=msedge),  pip install playwright  y la variable WILDFLY_HOME apuntando a la
instalación de WildFly (I02 consulta la DLQ con jboss-cli).
Uso:  python pruebas/prueba_seguridad.py
Deja en la base dos pacientes de prueba por corrida (seguridad.NNNNN y seguridad.sql.NNNNN@mediconecta.test) y un
turno cancelado de la Dra. Ana Lopez. No toca los usuarios de demo.
"""
import http.client as cliente_http
import json, os, random, re, subprocess, sys, time, urllib.error, urllib.request
from playwright.sync_api import sync_playwright
import ssl
# MediConecta corre por HTTPS con el certificado autofirmado que WildFly genera para localhost (SEC-W04):
# las pruebas lo aceptan sin validarlo. Sólo sirve para el entorno local.
urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "https://localhost:8443/mediconecta/"
EXT = "http://localhost:8080/externos/"
ADMINISTRATIVO = "carla.admin@mediconecta.test"
PROFESIONAL = "ana.lopez@mediconecta.test"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
WILDFLY_HOME = os.environ.get("WILDFLY_HOME")
DLQ = "/subsystem=messaging-activemq/server=default/jms-queue=DLQ"
ok = True


def jboss_cli(comando):
    """Ejecuta una operación de administración de WildFly y devuelve su salida."""
    script = os.path.join(WILDFLY_HOME, "bin", "jboss-cli.bat" if os.name == "nt" else "jboss-cli.sh")
    r = subprocess.run([script, "--connect", "--command=" + comando], capture_output=True, text=True, timeout=90,
                       stdin=subprocess.DEVNULL, env={**os.environ, "NOPAUSE": "true"})
    return r.stdout


def mensajes_en_dlq():
    m = re.search(r'"result" => (\d+)L', jboss_cli(DLQ + ":count-messages"))
    return int(m.group(1)) if m else -1


def consola(**modos):
    """Cambia el estado de los sistemas externos simulados (EN_LINEA, CAIDO...)."""
    pedido = urllib.request.Request(EXT + "api/consola/estado", method="PUT", data=json.dumps(modos).encode(),
                                    headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(pedido, timeout=10) as r:
        return r.status


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def http(url, metodo="GET", cuerpo=None):
    pedido = urllib.request.Request(url, method=metodo, data=None if cuerpo is None else json.dumps(cuerpo).encode())
    if cuerpo is not None:
        pedido.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(pedido, timeout=10) as r:
            return r.status, r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8")


def sin_seguir(metodo, url_http, cuerpo=None):
    """Pedido por http:// sin seguir redirecciones: (estado, Location)."""
    ruta = url_http.split("localhost:8080", 1)[1]
    conexion = cliente_http.HTTPConnection("localhost", 8080, timeout=10)
    conexion.request(metodo, ruta, body=None if cuerpo is None else json.dumps(cuerpo),
                     headers={"Content-Type": "application/json"} if cuerpo is not None else {})
    r = conexion.getresponse()
    return r.status, r.getheader("Location") or ""


def cookie_de_sesion():
    """Cabecera Set-Cookie de JSESSIONID al abrir el login por HTTPS."""
    conexion = cliente_http.HTTPSConnection("localhost", 8443, timeout=10, context=ssl._create_unverified_context())
    conexion.request("GET", "/mediconecta/login.xhtml")
    r = conexion.getresponse()
    return next((v for k, v in r.getheaders() if k.lower() == "set-cookie" and v.startswith("JSESSIONID")), "")


def login_api(email, clave):
    return http(B + "api/v1/auth/login", "POST", {"email": email, "password": clave})


def api(ruta, metodo="GET", cuerpo=None, token=None, tipo="application/json", crudo=None):
    """Pedido a la API con sus cabeceras: (estado, cabeceras, cuerpo)."""
    datos = crudo if crudo is not None else (None if cuerpo is None else json.dumps(cuerpo).encode())
    pedido = urllib.request.Request(B + "api/v1/" + ruta, method=metodo, data=datos)
    if datos is not None:
        pedido.add_header("Content-Type", tipo)
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(pedido, timeout=10) as r:
            return r.status, dict(r.headers), r.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode("utf-8")


def sin_fecha(respuesta):
    """Estado y cuerpo de error sin la marca de tiempo, que es lo único que cambia entre dos respuestas."""
    estado, cuerpo = respuesta
    datos = json.loads(cuerpo)
    datos.pop("fecha", None)
    return estado, datos


def pagina(br):
    return br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()


def login_web(br, email, clave):
    """Devuelve (página, ingresó). Si no ingresó, la página queda en el login con el mensaje de error."""
    pg = pagina(br)
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", clave)
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg, "login.xhtml" not in pg.url


def login_web_con_estado(br, email, clave):
    """Como login_web, y además el código HTTP con que respondió el envío del formulario."""
    pg = pagina(br)
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", clave)
    with pg.expect_response(lambda r: r.request.method == "POST" and "login.xhtml" in r.url) as respuesta:
        with pg.expect_navigation():
            pg.click("#formLogin button:has-text('Ingresar')")
    return pg, "login.xhtml" not in pg.url, respuesta.value.status


def registrar_paciente(br, email, clave, nombre="Paciente Seguridad"):
    pg = pagina(br)
    pg.goto(B + "registro.xhtml")
    pg.fill("#formRegistro\\:nombre", nombre)
    pg.fill("#formRegistro\\:email", email)
    pg.fill("#formRegistro\\:password", clave)
    pg.fill("#formRegistro\\:repeticion", clave)
    pg.click("#formRegistro button:has-text('Registrarme')")
    pg.wait_for_url("**/login.xhtml*")
    pg.context.close()


def fecha_libre():
    """Un horario de diciembre al azar, lejos de los que usan las otras pruebas (días 1 a 6)."""
    return "%02d/12/2026 %02d:%02d" % (random.randint(20, 28), random.randint(7, 19), random.randint(0, 59))


def publicar_horario(br, fecha):
    prof, _ = login_web(br, PROFESIONAL, "demo1234")
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", fecha)
    prof.keyboard.press("Escape")
    with prof.expect_navigation():
        prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
    publicado = "Disponibilidad publicada" in prof.content()
    prof.context.close()
    return publicado


def publicar_horario_libre(br):
    """Un horario libre publicado: si ya existe uno igual de otra corrida (D-23 no lo deja repetir), elige otro."""
    for _ in range(15):
        fecha = fecha_libre()
        if publicar_horario(br, fecha):
            return fecha
    raise AssertionError("no se encontró un horario libre en diciembre")


def reservar_y_pagar(br, email, clave, fecha):
    """El paciente reserva ese horario de la Dra. Ana Lopez y lo paga. Devuelve su página (en la Agenda)."""
    dia, hora = fecha.split(" ")
    pac, _ = login_web(br, email, clave)
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    with pac.expect_navigation():
        pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator(
            "button:has-text('Reservar')").first.click()
    pac.fill("#formConfirmacion\\:titular", "Paciente Seguridad")
    pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
    pac.fill("#formConfirmacion\\:vencimiento", ""); pac.fill("#formConfirmacion\\:vencimiento", "12/29")
    pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
    pac.click("#formConfirmacion\\:btnConfirmar")
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    return pac


def cancelar_turno(pac, fecha):
    dia, hora = fecha.split(" ")
    pac.goto(B + "agenda.xhtml")
    pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=dia + " · " + hora).locator(
        "button:has-text('Cancelar')").first.click()
    with pac.expect_navigation():
        pac.locator(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')").click()


def fila_de_pago(pac, fecha):
    """Texto de la fila de Pagos del turno de esa fecha (la columna Turno muestra "dd/mm/aaaa hh:mm")."""
    fila = pac.locator("#tablaPagos tr, [id$='tablaPagos'] tr", has_text=fecha)
    return fila.first.inner_text() if fila.count() else ""


def pacientes_en_historia(prof):
    prof.goto(B + "historia.xhtml")
    return prof.locator("#formSeleccion\\:paciente option").all_inner_texts()


with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    LOG_SERVIDOR = os.path.join(WILDFLY_HOME, "standalone", "log", "server.log") if WILDFLY_HOME else None
    inicio_corrida = os.path.getsize(LOG_SERVIDOR) if LOG_SERVIDOR else 0
    sufijo = random.randint(10000, 99999)
    email, clave = "seguridad.%d@mediconecta.test" % sufijo, "demo1234"
    nombre = "Paciente Seguridad %d" % sufijo
    registrar_paciente(br, email, clave, nombre)

    # ---- W01. Bloqueo tras 5 intentos fallidos ------------------------------------------------
    print("-- W01. Bloqueo tras 5 intentos fallidos")
    respuesta_incorrecta = login_api(email, "incorrecta")
    check("W01. una cuenta que no existe recibe la misma respuesta que una contraseña incorrecta",
          sin_fecha(login_api("no.existe.%d@mediconecta.test" % sufijo, "incorrecta")) == sin_fecha(respuesta_incorrecta))
    for _ in range(3):
        login_api(email, "incorrecta")
    check("W01. con 4 fallos la contraseña correcta todavía entra (API)", login_api(email, clave)[0] == 200)

    for _ in range(4):
        login_api(email, "incorrecta")
    web, ingreso, estado_web = login_web_con_estado(br, email, "incorrecta")  # quinto fallo, por la web: los dos caminos suman
    check("W01. el login correcto puso el contador en cero (4 fallos más no bloquean)", not ingreso)
    check("W01. el quinto fallo seguido, por la web, ya responde 429 con el mensaje genérico (%d)" % estado_web,
          estado_web == 429 and "Credenciales inválidas" in web.content())
    web.context.close()

    bloqueada = api("auth/login", "POST", {"email": email, "password": clave})
    check("W01. tras 5 fallos la API rechaza hasta la contraseña correcta: 429 con Retry-After (%d, %s)"
          % (bloqueada[0], bloqueada[1].get("Retry-After")),
          bloqueada[0] == 429 and int(bloqueada[1].get("Retry-After") or 0) > 0)
    inexistente = "no.existe.bloqueo.%d@mediconecta.test" % sufijo
    for _ in range(5):
        login_api(inexistente, "incorrecta")
    check("W01. un email que no existe recibe exactamente lo mismo tras 5 fallos: el 429 no revela qué cuentas existen",
          sin_fecha((bloqueada[0], bloqueada[2])) == sin_fecha(login_api(inexistente, "incorrecta")))
    web, ingreso, estado_web = login_web_con_estado(br, email, clave)
    check("W01. la web también rechaza la contraseña correcta, con el mensaje genérico y 429 (%d)" % estado_web,
          not ingreso and estado_web == 429 and "Credenciales inválidas" in web.content()
          and "bloque" not in web.inner_text(".ui-messages, .ui-growl, form").lower())
    check("W01. el login avisa la regla a todos (5 intentos, 15 minutos)",
          "5 intentos fallidos" in web.content() and "15 minutos" in web.content())
    web.context.close()

    adm, _ = login_web(br, ADMINISTRATIVO, "demo1234")
    adm.goto(B + "usuarios.xhtml")
    fila = adm.locator("#formUsuarios\\:tablaUsuarios tr", has_text=email)
    check("W01. el administrativo ve la cuenta bloqueada y hasta qué hora", re.search(r"Bloqueada hasta\s\d\d:\d\d", fila.inner_text()))
    with adm.expect_navigation():
        fila.locator("button:has-text('Desbloquear')").click()
    check("W01. el administrativo la desbloquea", "Desbloqueaste la cuenta" in adm.content())
    check("W01. la fila ya no figura bloqueada",
          "Bloqueada" not in adm.locator("#formUsuarios\\:tablaUsuarios tr", has_text=email).inner_text())
    adm.context.close()
    check("W01. después del desbloqueo la contraseña correcta entra (API)", login_api(email, clave)[0] == 200)
    web, ingreso = login_web(br, email, clave)
    check("W01. y por la web", ingreso)
    web.context.close()

    # ---- C01. Errores de la API en Problem Details (RFC 9457) -----------------------------------
    print("-- C01. Errores de la API en Problem Details (RFC 9457)")
    tk_pac = json.loads(login_api(email, clave)[1])["token"]
    tk_adm = json.loads(login_api(ADMINISTRATIVO, "demo1234")[1])["token"]
    casos = [("sin token", api("turnos"), 401, "no_autenticado"),
             ("token adulterado", api("turnos", token="eyJhbGciOiJIUzI1NiJ9.e30.firma"), 401, "token_invalido"),
             ("rol sin permiso (administrativo)", api("turnos", token=tk_adm), 403, "prohibido"),
             ("turno inexistente", api("turnos/999999", token=tk_pac), 404, "no_encontrado"),
             ("método no admitido", api("turnos", "DELETE", token=tk_pac), 405, "metodo_no_permitido"),
             ("JSON mal formado", api("auth/login", "POST", crudo=b'{"email": "x@x.test", '), 400, "pedido_invalido"),
             ("JSON sin datos", api("auth/login", "POST", {}), 422, "datos_invalidos"),
             ("cuerpo en texto plano", api("auth/login", "POST", crudo=b"hola", tipo="text/plain"), 415, "tipo_no_soportado")]
    # Variables propias del ciclo (caso_*): nombre, estado o cuerpo son del resto de la prueba y no se pueden pisar.
    for caso_nombre, (caso_estado, caso_cabeceras, caso_cuerpo), caso_esperado, caso_codigo in casos:
        datos = json.loads(caso_cuerpo)
        check("C01. %s: %d %s en application/problem+json, con type, title, detail e instance (%d %s)"
              % (caso_nombre, caso_esperado, caso_codigo, caso_estado, datos.get("error")),
              caso_estado == caso_esperado and caso_cabeceras.get("Content-Type", "").startswith("application/problem+json")
              and datos.get("status") == caso_esperado and datos.get("error") == caso_codigo
              and datos.get("type", "").endswith("problemas.md#" + caso_codigo) and datos.get("title") and datos.get("detail")
              and datos.get("instance", "").startswith("/mediconecta/api/v1/"))
    internos = [caso[0] for caso in casos if re.search(r"Exception|ar\.edu|RESTEASY|line no|column", caso[1][2])]
    check("C01. ningún error muestra trazas ni nombres internos%s" % ("" if not internos else " (" + ", ".join(internos) + ")"),
          not internos)
    check("C01. todo 401 trae el desafío WWW-Authenticate: Bearer",
          all("Bearer" in caso[1][1].get("WWW-Authenticate", "") for caso in casos if caso[2] == 401))

    # ---- W06. Inyección SQL en el login -------------------------------------------------------
    print("-- W06. Login resistente a inyección SQL")
    ataques = ["' OR '1'='1", "' OR 1=1 --", "\" OR \"\"=\"", "'; DROP TABLE usuario; --",
               "juan.perez@mediconecta.test'--", "juan.perez@mediconecta.test' OR '1'='1",
               "' UNION SELECT password FROM usuario --"]
    generico = sin_fecha(login_api("no.existe.%d@mediconecta.test" % sufijo, "incorrecta"))
    rastros = ("sql", "syntax", "postgres", "exception", "org.", "hibernate", "jdbc", "psql")
    # Contra la cuenta real, sólo 4 contraseñas: con 5 fallos se bloquearía (W01) y el resto de la prueba no
    # podría ingresar. El ingreso correcto de más abajo pone el contador en cero.
    respuestas = [login_api(a, a) for a in ataques] + [login_api(email, a) for a in ataques[:4]]
    check("W06. API: los %d ataques (en el email y en la contraseña) reciben el 401 genérico" % len(respuestas),
          all(sin_fecha(r) == generico for r in respuestas))
    check("W06. API: ninguna respuesta tiene rastros de la base (SQL, excepciones, nombres internos)",
          not any(t in r[1].lower() for r in respuestas for t in rastros))
    resultados_web = []
    for ataque in ataques[:4]:
        web, ingreso = login_web(br, ataque, ataque)
        texto = web.content().lower()
        resultados_web.append(not ingreso and "credenciales inválidas" in texto and not any(t in texto for t in rastros[:4]))
        web.context.close()
    check("W06. web: los ataques no entran y muestran el mensaje genérico, sin errores de la base", all(resultados_web))
    pg = pagina(br)
    pg.goto(B + "registro.xhtml")
    pg.fill("#formRegistro\\:nombre", "Prueba")
    # El campo es type="email" y el navegador no dejaría enviarlo: se lo pasa a texto para que el ataque
    # llegue al servidor, como lo mandaría alguien que arma el pedido a mano.
    pg.locator("#formRegistro\\:email").evaluate("campo => campo.type = 'text'")
    pg.fill("#formRegistro\\:email", "x' OR '1'='1")
    pg.fill("#formRegistro\\:password", clave)
    pg.fill("#formRegistro\\:repeticion", clave)
    with pg.expect_navigation():
        pg.click("#formRegistro button:has-text('Registrarme')")
    check("W06. registro: un email con SQL llega al servidor y se rechaza por formato, sin error de la base",
          "registro.xhtml" in pg.url and "email válido" in pg.content()
          and not any(t in pg.content().lower() for t in rastros[:4]))
    pg.context.close()
    nombre_raro = "Robert'); DROP TABLE usuario; --"
    email_raro = "seguridad.sql.%d@mediconecta.test" % sufijo
    registrar_paciente(br, email_raro, clave, nombre_raro)
    raro, ingreso = login_web(br, email_raro, clave)
    raro.goto(B + "perfil.xhtml")
    check("W06. registro: un nombre con SQL se guarda como texto literal (se ve igual en Mi perfil)",
          ingreso and nombre_raro in raro.inner_text("dl.datos-perfil"))
    raro.context.close()
    check("W06. la base sigue intacta: la tabla de usuarios responde y el paciente de prueba entra",
          login_api(email, clave)[0] == 200)

    # ---- W04. Cookie de sesión y HTTPS --------------------------------------------------------
    print("-- W04. Cookie de sesión y HTTPS")
    estado, destino = sin_seguir("GET", "http://localhost:8080/mediconecta/login.xhtml")
    check("W04. una página pedida por http:// redirige a https:// (%d)" % estado,
          estado in (301, 302, 307, 308) and destino.startswith("https://localhost:8443/mediconecta/"))
    estado, destino = sin_seguir("POST", "http://localhost:8080/mediconecta/api/v1/auth/login",
                                 {"email": email, "password": clave})
    check("W04. la API tampoco atiende por http:// (redirige, no devuelve un token en claro)",
          estado in (301, 302, 307, 308) and destino.startswith("https://"))
    atributos = [a.strip().lower() for a in cookie_de_sesion().split(";")]
    check("W04. la cookie de sesión lleva HttpOnly", "httponly" in atributos)
    check("W04. la cookie de sesión lleva Secure", "secure" in atributos)
    check("W04. la cookie de sesión lleva SameSite=Lax", "samesite=lax" in atributos)
    pac, ingreso = login_web(br, email, clave)
    check("W04. con la sesión iniciada, JavaScript no puede leer la cookie de sesión",
          ingreso and "JSESSIONID" not in pac.evaluate("document.cookie"))
    cookie = next(c for c in pac.context.cookies() if c["name"] == "JSESSIONID")
    check("W04. y el navegador la guarda como HttpOnly, Secure y Lax",
          cookie["httpOnly"] and cookie["secure"] and cookie["sameSite"] == "Lax")
    pac.context.close()

    # ---- D01. Historia clínica sólo con turno confirmado --------------------------------------
    print("-- D01. Historia clínica sólo con turno confirmado")
    consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR")
    prof, _ = login_web(br, PROFESIONAL, "demo1234")
    check("D01. sin turno, el paciente no figura entre los que la profesional puede ver",
          not any(nombre in o for o in pacientes_en_historia(prof)))
    fecha = publicar_horario_libre(br)
    pac = reservar_y_pagar(br, email, clave, fecha)
    check("D01. el paciente confirma un turno con la Dra. Ana Lopez (%s)" % fecha, "Turno confirmado" in pac.content())
    opcion = next((o for o in pacientes_en_historia(prof) if nombre in o), None)
    check("D01. con el turno confirmado, el paciente aparece en la historia clínica de la profesional", opcion is not None)
    prof.select_option("#formSeleccion\\:paciente", label=opcion)
    with prof.expect_navigation():
        prof.click("#formSeleccion button:has-text('Ver historia')")
    diagnostico = "Diagnóstico de prueba D01 %d" % sufijo
    prof.fill("#formHistoria\\:descripcion", diagnostico)
    with prof.expect_navigation():
        prof.click("#formHistoria button:has-text('Guardar')")
    check("D01. la profesional abre la historia y registra un diagnóstico", diagnostico in prof.content())
    pac.goto(B + "historia.xhtml")
    check("D01. el paciente ve el diagnóstico en su historia y que la Dra. Ana Lopez la consultó",
          diagnostico in pac.content() and "Ana Lopez" in pac.content())

    cancelar_turno(pac, fecha)  # la profesional sigue con la historia abierta en su pantalla
    check("D01. el paciente cancela el turno", "Turno cancelado" in pac.content())
    prof.fill("#formHistoria\\:descripcion", "Intento después de la cancelación %d" % sufijo)
    with prof.expect_navigation():
        prof.click("#formHistoria button:has-text('Guardar')")
    check("D01. con la historia todavía abierta, el servidor rechaza la nueva entrada (ya no hay turno confirmado)",
          "No tenés acceso a la historia clínica de este paciente" in prof.content())
    pac.goto(B + "historia.xhtml")
    check("D01. y la entrada rechazada no quedó guardada", "Intento después de la cancelación" not in pac.content())
    check("D01. al recargar, el paciente ya no figura entre los de la profesional",
          not any(nombre in o for o in pacientes_en_historia(prof)))
    prof.context.close()
    pac.context.close()

    # ---- I02. Dead Letter Channel ------------------------------------------------------------
    print("-- I02. Un mensaje que falla va a la DLQ")
    if not WILDFLY_HOME:
        check("I02. definí WILDFLY_HOME para consultar la DLQ con jboss-cli", False)
    else:
        log = os.path.join(WILDFLY_HOME, "standalone", "log", "server.log")
        inicio_log = os.path.getsize(log)
        consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR")
        en_dlq_antes = mensajes_en_dlq()
        fecha = publicar_horario_libre(br)
        dia, hora = fecha.split(" ")
        pac = reservar_y_pagar(br, email, clave, fecha)
        check("I02. el paciente confirma y paga un turno (%s)" % fecha, "Turno confirmado" in pac.content())

        origen = None
        try:
            consola(pasarela="CAIDO")
            pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=dia + " · " + hora).locator(
                "button:has-text('Cancelar')").first.click()
            with pac.expect_navigation():
                pac.locator(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')").click()
            check("I02. con la pasarela caída, la cancelación igual se acepta (el reintegro es asincrónico)",
                  "Turno cancelado" in pac.content())

            fin = time.time() + 60
            while time.time() < fin and mensajes_en_dlq() <= en_dlq_antes:
                time.sleep(3)
            en_dlq = mensajes_en_dlq()
            with open(log, encoding="utf-8", errors="replace") as f:
                f.seek(inicio_log)
                intentos = re.findall(r"Pasarela no disponible para el reintegro del turno (\d+) \(intento (\d)\)", f.read())
            check("I02. Facturación lo intentó 3 veces (log: intentos %s)" % [n for _, n in intentos],
                  [n for _, n in intentos] == ["1", "2", "3"])
            check("I02. después del tercer fallo el mensaje quedó en jms.queue.DLQ (%d -> %d)" % (en_dlq_antes, en_dlq),
                  en_dlq == en_dlq_antes + 1)
            listado = jboss_cli(DLQ + ":list-messages")
            check("I02. el mensaje de la DLQ es la cancelación (tipoEvento TURNO_CANCELADO, del tópico de turnos)",
                  "TURNO_CANCELADO" in listado and "turnos" in listado)
            # La DLQ guarda de qué cola vino cada mensaje: la suscripción durable de Facturación.
            origen = re.search(r'"_AMQ_ORIG_QUEUE" => "([^"]+)"', listado)
            check("I02. y guarda de qué suscripción vino (%s)" % (origen.group(1) if origen else "?"),
                  origen is not None and "facturacion" in origen.group(1))
            pac.goto(B + "pagos.xhtml")
            check("I02. mientras está en la DLQ el pago no figura reembolsado (nada se perdió ni se hizo a medias)",
                  (lambda fila: fila != "" and "Reembolsado" not in fila)(fila_de_pago(pac, fecha)))
        finally:
            consola(pasarela="EN_LINEA")

        # Reproceso: WildFly no tiene "retry" en sus colas; se mueve el mensaje de vuelta a la cola de origen.
        # Antes hay que esperar que el Circuit Breaker de la pasarela se cierre: tras 3 fallas deja de llamarla
        # 30 segundos aunque ya esté en línea, y el mensaje volvería a la DLQ.
        time.sleep(35)
        if origen:
            jboss_cli('/subsystem=messaging-activemq/server=default/runtime-queue=jms.queue.DLQ:move-messages('
                      'other-queue-name=%s)' % origen.group(1))  # sin comillas: el .bat de Windows las rompe
        fin = time.time() + 30
        reembolsado = False
        while time.time() < fin and not reembolsado:
            time.sleep(3)
            pac.goto(B + "pagos.xhtml")
            reembolsado = "Reembolsado" in fila_de_pago(pac, fecha)
        check("I02. con la pasarela en línea, el mensaje vuelve a Facturación y el pago queda Reembolsado", reembolsado)
        check("I02. la DLQ quedó vacía de nuevo", mensajes_en_dlq() == 0)
        pac.context.close()

    # ---- D02. Logs sin datos sensibles ---------------------------------------------------------
    print("-- D02. Logs sin datos sensibles y email enmascarado")
    if not LOG_SERVIDOR:
        check("D02. definí WILDFLY_HOME para leer el log del servidor", False)
    else:
        email_alta = "seguridad.alta.%d@mediconecta.test" % sufijo
        adm, _ = login_web(br, ADMINISTRATIVO, "demo1234")
        adm.goto(B + "usuarios.xhtml")
        adm.select_option("#formAlta\\:rol", "PACIENTE")
        adm.wait_for_selector("#formAlta\\:obraSocial")
        adm.fill("#formAlta\\:nombre", "Alta Seguridad %d" % sufijo)
        adm.fill("#formAlta\\:email", email_alta)
        adm.fill("#formAlta\\:passwordTemporal", "Temporal.2026")
        with adm.expect_navigation():
            adm.click("#formAlta button:has-text('Crear usuario')")
        check("D02. el administrativo da de alta un paciente", "Creaste a" in adm.content())
        adm.context.close()
        time.sleep(2)  # que terminen de escribirse las últimas líneas (los avisos son asincrónicos)

        with open(LOG_SERVIDOR, encoding="utf-8", errors="replace") as archivo:
            archivo.seek(inicio_corrida)
            escrito = archivo.read()
        lineas = len(escrito.splitlines())
        ruido = [l for l in escrito.splitlines() if "WFLYEJB0034" in l and re.search(r"(Turnos|Auth|Profesionales)Resource", l)]
        check("D-34. los rechazos esperados de la API (401, 403, 404 y 429) no dejan errores con traza en el log%s"
              % ("" if not ruido else " (" + ruido[0][:120] + ")"), not ruido)
        sensibles = {"email del paciente de prueba": email, "email del paciente con nombre raro": email_raro,
                     "email dado de alta": email_alta, "email del administrativo": ADMINISTRATIVO,
                     "email de la profesional": PROFESIONAL, "nombre del paciente": nombre,
                     "contraseña": clave, "contraseña temporal": "Temporal.2026", "diagnóstico": diagnostico}
        presentes = [que for que, valor in sensibles.items() if valor in escrito]
        check("D02. en las %d líneas de log de esta corrida no hay emails completos, nombres, contraseñas ni "
              "diagnósticos%s" % (lineas, "" if not presentes else " (aparecen: " + ", ".join(presentes) + ")"),
              lineas > 0 and not presentes)
        check("D02. los avisos por email muestran el destinatario enmascarado ([SMTP simulado] Para: se***@...)",
              "[SMTP simulado] Para: se***@mediconecta.test" in escrito)
        check("D02. el alta de usuario muestra los emails enmascarados (el nuevo y el del administrativo)",
              "Alta de usuario PACIENTE (se***@mediconecta.test) por el administrativo ca***@mediconecta.test" in escrito)

    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
