"""Prueba de punta a punta de la Entrega Obligatoria N.º 2 sobre la app desplegada.

Recorre con un navegador real (y con llamadas HTTP directas) todo lo que se defiende en esta entrega:

  A. Contrato SOAP: el WSDL del legado de la obra social está publicado.
  B. API REST propia: pública, login que entrega un JWT, 401 sin token o con un token adulterado, 200 con
     Bearer y 404 para un turno ajeno.
  N. Patrón State por la API: cancelar un turno ya cancelado o todavía en espera devuelve 409.
  C. Flujo crítico: cotización por SOAP, pago rechazado por la pasarela (el turno sigue en espera) y pago aprobado.
  D. Cola punto a punto: la autorización se resuelve sola, llega la notificación y Facturación genera el reclamo.
  F. La falla a mitad del flujo crítico ya no está en la aplicación (D-42): Pagos no tiene la herramienta de
     demostración y sí los reintegros pendientes. El rollback y la compensación los prueba CompensacionIT (Arquillian).
  G. Legado caído: la cotización usa el fallback (caché o cobertura pendiente) y el turno se confirma igual.
  G2. SOAP Fault de negocio (afiliado de baja): el paciente ve un mensaje propio, no el Fault; el turno se cotiza
      como particular y se confirma.
  H. Cancelación: Facturación reintegra el pago (REST) y anula el reclamo, de forma asincrónica.
  I. Telemedicina: el profesional crea la sala por REST y el paciente ve el enlace.
  J. Auditoría por interceptor: el paciente ve qué profesional consultó su historia clínica.
  K. Temas visuales: cambio en tiempo real sin recargar y persistencia al navegar.
  L. Roles: el administrativo accede a Pagos y Usuarios y el profesional ya no tiene Pagos (vista, URL y API REST).
  M. Registro aparte y sólo de pacientes; alta de un profesional por el administrativo con contraseña temporal,
     cambio obligatorio en el primer ingreso y "Mi perfil".

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados,
Google Chrome y  pip install playwright
Uso:  python pruebas/prueba_entrega4.py
Usa los usuarios de demo (ver SETUP.md). Deja en la base un paciente y un profesional de prueba por corrida.
El Monitor de eventos tiene su propia prueba (prueba_monitor.py).
"""
import os
import base64, json, random, sys, time, urllib.error, urllib.request
from datetime import date, timedelta
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
PACIENTE, PROFESIONAL = "juan.perez@mediconecta.test", "ana.lopez@mediconecta.test"
ADMINISTRATIVO = "carla.admin@mediconecta.test"
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def http(url, usuario=None, clave=None, metodo="GET", cuerpo=None, token=None, basic=False):
    """Con usuario y clave pide primero un JWT (POST /auth/login) y lo envía como Bearer."""
    pedido = urllib.request.Request(url, method=metodo, data=None if cuerpo is None else json.dumps(cuerpo).encode())
    if cuerpo is not None:
        pedido.add_header("Content-Type", "application/json")
    if usuario and basic:
        pedido.add_header("Authorization", "Basic " + base64.b64encode(f"{usuario}:{clave}".encode()).decode())
    elif usuario:
        estado, cuerpo_login, cabeceras = iniciar_sesion(usuario, clave)
        if estado != 200:
            return estado, cuerpo_login, cabeceras
        token = json.loads(cuerpo_login)["token"]
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(pedido, timeout=10) as r:
            return r.status, r.read().decode("utf-8"), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8"), dict(e.headers)


def iniciar_sesion(usuario, clave):
    return http(B + "api/v1/auth/login", metodo="POST", cuerpo={"email": usuario, "password": clave})


def token_de(usuario, clave="demo1234"):
    return json.loads(iniciar_sesion(usuario, clave)[1])["token"]


def turno_id(fecha):
    """Id del turno del paciente de demo en esa fecha ("dd/mm/aaaa hh:mm"), buscado por la API."""
    dia, hora = fecha.split(" ")
    d, m, a = dia.split("/")
    iso = "%s-%s-%sT%s" % (a, m, d, hora)
    turnos = json.loads(http(B + "api/v1/turnos", token=token_de(PACIENTE))[1])
    return next((t["id"] for t in turnos if t["fechaHora"].startswith(iso) and t["estado"] != "CANCELADO"), 0)


def b64url(texto):
    return base64.urlsafe_b64encode(texto.encode()).decode().rstrip("=")


def consola(**cambios):
    return http(EXT + "api/consola/estado", metodo="PUT", cuerpo=cambios)


def login(br, email, inicio="agenda.xhtml", clave="demo1234"):
    pg = br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", clave)
    pg.click("#formLogin button:has-text('Ingresar')")
    pg.wait_for_url("**/" + inicio + "*")
    return pg


def mensaje(pg):
    return " ".join(pg.locator(".ui-growl-message").all_inner_texts())


def publicar(prof, fecha, modalidad="PRESENCIAL"):
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", fecha)  # p:datePicker: también acepta la fecha tipeada
    prof.keyboard.press("Escape")                     # cierra el calendario para que no tape el botón
    prof.select_option("#formPublicar\\:modalidad", modalidad)
    # Clic por DOM: si el calendario quedó abierto tapa el botón y un clic con el puntero no llega.
    with prof.expect_navigation():
        prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
    return "Disponibilidad publicada" in prof.content()


def reservar(pac, fecha):
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    pac.click("button:has-text('Ver turnos disponibles')")
    pac.wait_for_load_state()
    dia, hora = fecha.split(" ")
    pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Reservar')").click()
    pac.wait_for_load_state()


def pagar(pac, tarjeta="4111 1111 1111 1111"):
    if pac.locator("#formConfirmacion\\:numeroTarjeta").count():
        pac.fill("#formConfirmacion\\:titular", "Juan Perez")
        pac.fill("#formConfirmacion\\:numeroTarjeta", tarjeta)
        pac.fill("#formConfirmacion\\:vencimiento", ""); pac.fill("#formConfirmacion\\:vencimiento", "12/29")
        pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
        # Con copago se paga en dos pasos: el botón revisa la tarjeta y abre el diálogo con el importe (D-29).
        pac.click("#formConfirmacion\\:btnConfirmar")
        with pac.expect_navigation():
            pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    else:
        with pac.expect_navigation():
            pac.click("#formConfirmacion\\:btnConfirmarSinPago")
    return " ".join(pac.locator(".ui-growl-message").all_inner_texts())


def esperar(pg, url, condicion, segundos):
    fin = time.time() + segundos
    while time.time() < fin:
        pg.goto(url)
        if condicion(pg.content()):
            return True
        time.sleep(3)
    return False


def dia_desde_hoy(dias):
    """Un día a tantos días de hoy ("dd/mm/aaaa"): la prueba no depende de la fecha en que se corre."""
    return (date.today() + timedelta(days=dias)).strftime("%d/%m/%Y")


def fecha_al_azar(dia):
    """Un horario del día "01" a "04" de la prueba: a 13-16 días de hoy, lejos de los que usan las otras."""
    return "%s %02d:%02d" % (dia_desde_hoy(12 + int(dia)), random.randint(7, 19), random.randint(0, 11) * 5)


def publicar_libre(prof, dia, modalidad="PRESENCIAL"):
    """Publica un horario libre de ese día: si ya existe uno igual de otra corrida (D-23 no lo deja repetir), elige otro."""
    for _ in range(15):
        fecha = fecha_al_azar(dia)
        if publicar(prof, fecha, modalidad):
            return fecha
    raise AssertionError("no se encontró un horario libre el %s" % dia_desde_hoy(12 + int(dia)))


consola(obraSocial="EN_LINEA", autorizaciones="APROBAR", pasarela="EN_LINEA", videoconsulta="EN_LINEA")

# ---- A. SOAP --------------------------------------------------------------------------------
estado, wsdl, _ = http(EXT + "CoberturaService?wsdl")
check("A. WSDL del legado publicado (CoberturaService, operaciones validarCobertura y solicitarAutorizacion)",
      estado == 200 and 'name="CoberturaService"' in wsdl and "solicitarAutorizacion" in wsdl and "validarCobertura" in wsdl)

# ---- B. API REST ----------------------------------------------------------------------------
estado, cuerpo, _ = http(B + "api/v1/profesionales")
check("B. GET /api/v1/profesionales es público y devuelve JSON", estado == 200 and "Ana Lopez" in cuerpo)
estado, cuerpo, cabeceras = http(B + "api/v1/turnos")
check("B. GET /api/v1/turnos sin token: 401 con WWW-Authenticate: Bearer",
      estado == 401 and "Bearer" in cabeceras.get("WWW-Authenticate", "") and "no_autenticado" in cuerpo)
estado, cuerpo, _ = iniciar_sesion(PACIENTE, "incorrecta")
check("B. login con credenciales inválidas: 401 sin token", estado == 401 and "token" not in json.loads(cuerpo))
estado, cuerpo, _ = iniciar_sesion(PACIENTE, "demo1234")
sesion = json.loads(cuerpo) if estado == 200 else {}
TOKEN = sesion.get("token", "")
check("B. POST /api/v1/auth/login devuelve un JWT (tres partes, Bearer, con vencimiento y rol)",
      estado == 200 and TOKEN.count(".") == 2 and sesion.get("tipo") == "Bearer"
      and sesion.get("expiraEnSegundos", 0) > 0 and sesion.get("rol") == "PACIENTE")
estado, cuerpo, _ = http(B + "api/v1/turnos", token=TOKEN)
check("B. con el JWT en Authorization: Bearer: 200 con los turnos del paciente", estado == 200 and cuerpo.startswith("["))
cabecera, contenido, firma = TOKEN.split(".")
datos = json.loads(base64.urlsafe_b64decode(contenido + "=" * (-len(contenido) % 4)))
adulterado = cabecera + "." + b64url(json.dumps(dict(datos, rol="ADMINISTRATIVO"), separators=(",", ":"))) + "." + firma
estado, cuerpo, _ = http(B + "api/v1/turnos", token=adulterado)
check("B. JWT con el rol adulterado (firma inválida): 401 en JSON", estado == 401 and "token_invalido" in cuerpo)
sin_firma = b64url('{"alg":"none","typ":"JWT"}') + "." + contenido + "."
estado, _, _ = http(B + "api/v1/turnos", token=sin_firma)
check("B. JWT con alg none y sin firma: 401", estado == 401)
estado, _, _ = http(B + "api/v1/turnos", PACIENTE, "demo1234", basic=True)
check("B. HTTP Basic ya no se acepta en la API: 401", estado == 401)
estado, cuerpo, _ = http(B + "api/v1/turnos/999999999", PACIENTE, "demo1234")
check("B. turno inexistente o ajeno: 404 sin trazas", estado == 404 and "no_encontrado" in cuerpo and "Exception" not in cuerpo)

with sync_playwright() as p:
    br = p.chromium.launch(channel=os.environ.get("NAVEGADOR", "chrome"), headless=True)
    prof = login(br, PROFESIONAL)
    pac = login(br, PACIENTE)
    adm = login(br, ADMINISTRATIVO, "pagos.xhtml")

    # ---- L. Roles: el administrativo sólo ve Pagos y el profesional ya no tiene Pagos ------------
    menu = adm.inner_text(".sidebar .nav")
    check("L. el administrativo entra directo a Pagos, su menú tiene Pagos y Usuarios, y el perfil está en su tarjeta",
          "pagos.xhtml" in adm.url and "Pagos" in menu and "Usuarios" in menu
          and adm.locator(".sidebar .user-chip[title='Mi perfil']").count() == 1
          and "Agenda" not in menu and "Notificaciones" not in menu)
    adm.goto(B + "agenda.xhtml")
    check("L. si el administrativo escribe la URL de la Agenda, vuelve a Pagos", "pagos.xhtml" in adm.url)
    check("L. el profesional no tiene la solapa Pagos", "Pagos" not in prof.inner_text(".sidebar .nav"))
    prof.goto(B + "pagos.xhtml")
    check("L. si el profesional escribe la URL de Pagos, vuelve a la Agenda", "agenda.xhtml" in prof.url)
    estado, _, _ = http(B + "api/v1/turnos", ADMINISTRATIVO, "demo1234")
    check("L. la API REST de turnos rechaza al administrativo (403, @RolesAllowed)", estado == 403)

    # ---- C. Flujo crítico con pago rechazado y aprobado -----------------------------------
    f1 = publicar_libre(prof, "01")
    check("C. el profesional publica un horario presencial (" + f1 + ")", True)
    reservar(pac, f1)
    contenido = pac.content()
    check("C. la cotización consulta la cobertura por SOAP (OSDE, copago del 30 %)",
          "Consultada en línea a tu obra social" in contenido and "Copago del 30" in contenido)
    msg = pagar(pac, "4000 0000 0000 0002")
    check("C. tarjeta rechazada: se informa y el turno sigue en espera", "Pago rechazado" in msg
          and "Turno reservado temporalmente" in pac.content())
    msg = pagar(pac)
    check("C. tarjeta aprobada: turno confirmado y copago cobrado", "Turno confirmado" in msg and "9600" in msg.replace(".", ""))

    # ---- D. Cola punto a punto + Facturación ---------------------------------------------
    check("D. el paciente recibe la notificación de la autorización (resuelta por cola.autorizaciones)",
          esperar(pac, B + "notificaciones.xhtml", lambda c: "autorizó la prestación" in c, 30))
    check("D. Facturación genera el reclamo a la obra social (TURNO_AUTORIZADO), visible para el administrativo",
          esperar(adm, B + "pagos.xhtml", lambda c: "Pendiente de presentación" in c, 15))

    # ---- F. La falla a mitad del flujo crítico ya no vive en la aplicación (D-42) ----------------------
    # El rollback y la compensación los prueba CompensacionIT (Arquillian), que provoca la falla desde la prueba.
    adm.goto(B + "pagos.xhtml")
    check("F. Pagos ya no tiene la falla de demostración (D-42: se probó con Arquillian y se retiró de la aplicación)",
          "Armar falla antes del commit" not in adm.content() and "Demostración" not in adm.inner_text(".content"))
    check("F. y muestra los reintegros pendientes (D-06 y D-41)", "Reintegros pendientes" in adm.inner_text(".content"))

    # ---- G2. SOAP Fault de negocio: el legado responde, pero rechaza al afiliado (Eje C) ----------
    # Antes que G: el legado caído abre el circuito 30 s y en ese lapso la consulta ni llegaría al legado.
    consola(obraSocial="AFILIADO_DE_BAJA")
    f3b = publicar_libre(prof, "03")
    reservar(pac, f3b)
    contenido = pac.content()
    check("G2. con un SOAP Fault de negocio el paciente ve el motivo con un mensaje propio, nunca el texto del Fault",
          "afiliación está dada de baja" in contenido and "ERR-PAD" not in contenido and "Fault" not in contenido)
    check("G2. el turno se cotiza como particular, sin esperar a la obra social", "como particular" in contenido)
    msg = pagar(pac)
    check("G2. y se confirma: un rechazo de negocio no frena la reserva", "Turno confirmado" in msg)
    consola(obraSocial="EN_LINEA")

    # ---- G. Legado caído: fallback de cobertura -------------------------------------------
    consola(obraSocial="CAIDO")
    f3 = publicar_libre(prof, "03")
    reservar(pac, f3)
    contenido = pac.content()
    check("G. con el legado caído la cotización usa el fallback (caché o cobertura pendiente)",
          "no respondió" in contenido or "no responde" in contenido)
    msg = pagar(pac)
    check("G. el turno se confirma igual: el resto de la plataforma sigue funcionando", "Turno confirmado" in msg)
    consola(obraSocial="EN_LINEA")

    # ---- N. Patrón State por la API: una transición inválida devuelve 409 ------------------
    id3 = turno_id(f3)
    estado, cuerpo, _ = http(B + "api/v1/turnos/%s/cancelacion" % id3, metodo="POST")
    check("N. cancelar por la API sin token: 401", estado == 401)
    # Desde el 14/11 el profesional (turnos de su agenda) y el administrativo (cualquiera) también cancelan: esa
    # matriz, el 404 para un paciente ajeno y que el horario no se vuelva a ofrecer los prueba prueba_cancelacion.py.
    estado, cuerpo, _ = http(B + "api/v1/turnos/%s/cancelacion" % id3, metodo="POST", token=TOKEN)
    check("N. el paciente cancela su turno confirmado por la API: 200 y estado CANCELADO",
          estado == 200 and json.loads(cuerpo)["estado"] == "CANCELADO")
    estado, cuerpo, _ = http(B + "api/v1/turnos/%s/cancelacion" % id3, metodo="POST", token=TOKEN)
    check("N. cancelarlo otra vez es una transición inválida: 409 transicion_invalida",
          estado == 409 and "transicion_invalida" in cuerpo and "cancelado" in cuerpo and "Exception" not in cuerpo)
    estado, cuerpo, _ = http(B + "api/v1/turnos/%s" % id3, token=TOKEN)
    check("N. tras el 409 el turno sigue CANCELADO (la transacción se revirtió sin efectos)",
          estado == 200 and json.loads(cuerpo)["estado"] == "CANCELADO")
    estado, cuerpo, _ = http(B + "api/v1/turnos/999999999/cancelacion", metodo="POST", token=TOKEN)
    check("N. cancelar un turno inexistente o ajeno: 404", estado == 404)
    ana = next(p["id"] for p in json.loads(http(B + "api/v1/profesionales")[1]) if "Ana Lopez" in p["nombre"])
    ofrecidos = json.loads(http(B + "api/v1/profesionales/%s/disponibilidad" % ana)[1])
    d3, h3 = f3.split(" ")
    check("N. el horario del turno cancelado vuelve a ofrecerse a otros pacientes",
          any(t["fechaHora"].startswith("%s-%s-%sT%s" % (d3[6:], d3[3:5], d3[:2], h3)) for t in ofrecidos))
    check("N. la disponibilidad pública sólo ofrece horarios futuros",
          len(ofrecidos) > 0 and all(t["fechaHora"] > time.strftime("%Y-%m-%dT%H:%M") for t in ofrecidos))

    # ---- H. Cancelación con reintegro asincrónico -------------------------------------------
    pac.goto(B + "agenda.xhtml")
    pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=f1.replace(" ", " · ")).locator("button:has-text('Cancelar')").click()
    with pac.expect_navigation():
        pac.locator(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')").click()
    check("H. cancelación aceptada, con aviso del reintegro", "reintegro" in pac.content())
    check("H. Facturación reintegra el pago por REST (estado Reembolsado)",
          esperar(pac, B + "pagos.xhtml", lambda c: "Reembolsado" in c, 20))
    check("H. y anula el reclamo a la obra social", esperar(adm, B + "pagos.xhtml", lambda c: "Anulado" in c, 15))

    # ---- I. Telemedicina -------------------------------------------------------------------
    f4 = publicar_libre(prof, "04", "TELEMEDICINA")
    reservar(pac, f4)
    estado, cuerpo, _ = http(B + "api/v1/turnos/%s/cancelacion" % turno_id(f4), metodo="POST", token=TOKEN)
    check("N. cancelar un turno que todavía está en espera (HOLD): 409", estado == 409 and "en espera" in cuerpo)
    msg = pagar(pac)
    check("I. teleconsulta confirmada (precio con descuento de telemedicina)", "Turno confirmado" in msg)
    prof.goto(B + "telemedicina.xhtml")
    prof.locator("#formTelemedicina\\:tablaTeleconsultas tr", has_text=f4.split(" ")[0]).locator(
        "button:has-text('Iniciar videoconsulta')").first.click()
    prof.wait_for_load_state()
    check("I. el profesional crea la sala en el proveedor externo (REST)", "Abriste la sala" in prof.content()
          and "Entrar a la sala" in prof.content())
    pac.goto(B + "telemedicina.xhtml")
    check("I. el paciente ve el enlace para unirse", "Unirme a la consulta" in pac.content())

    # ---- J. Auditoría por interceptor --------------------------------------------------------
    prof.goto(B + "historia.xhtml")
    sel = prof.locator("#formSeleccion\\:paciente")
    sel.select_option(sel.locator("option", has_text="Juan Perez").first.get_attribute("value"))
    prof.click("button:has-text('Ver historia')")
    prof.wait_for_load_state()
    pac.goto(B + "historia.xhtml")
    check("J. el paciente ve que la profesional consultó su historia (interceptor @Auditado)",
          "Dra. Ana Lopez" in pac.inner_text("#formHistoria\\:tablaAccesos"))

    # ---- K. Temas visuales -----------------------------------------------------------------
    pac.goto(B + "agenda.xhtml")
    pac.evaluate("window.__sinRecargar = true")
    pac.click("button[data-tema-opcion='oscuro']")
    pac.wait_for_timeout(600)  # la transición de colores dura 0,25 s
    fondo = pac.evaluate("getComputedStyle(document.body).backgroundColor")
    check("K. el tema oscuro se aplica en tiempo real, sin recargar la página",
          pac.evaluate("document.documentElement.dataset.tema") == "oscuro" and pac.evaluate("window.__sinRecargar === true")
          and fondo == "rgb(15, 23, 42)")
    pac.goto(B + "pagos.xhtml")
    check("K. el tema elegido se mantiene al navegar (cookie)", pac.evaluate("document.documentElement.dataset.tema") == "oscuro")
    pac.click("button[data-tema-opcion='claro']")
    check("K. tema claro", pac.evaluate("document.documentElement.dataset.tema") == "claro")
    pac.click("button[data-tema-opcion='estandar']")
    check("K. vuelta al tema estándar", pac.evaluate("document.documentElement.dataset.tema") == "estandar")

    # ---- M. Registro sólo de pacientes, alta por el administrativo y contraseña temporal ------
    sufijo = random.randint(10000, 99999)
    nuevo = br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()
    nuevo.goto(B + "login.xhtml")
    nuevo.click("text=Registrate")
    nuevo.wait_for_url("**/registro.xhtml*")
    check("M. el login lleva a una pantalla de registro aparte, sin elegir rol",
          nuevo.locator("#formRegistro\\:rol").count() == 0 and "Crear cuenta de paciente" in nuevo.content())
    email_pac = "paciente.%d@mediconecta.test" % sufijo
    nuevo.fill("#formRegistro\\:nombre", "Paciente Prueba %d" % sufijo)
    nuevo.fill("#formRegistro\\:email", email_pac)
    nuevo.fill("#formRegistro\\:password", "demo1234")
    nuevo.fill("#formRegistro\\:repeticion", "demo1234")
    nuevo.click("#formRegistro button:has-text('Registrarme')")
    nuevo.wait_for_url("**/login.xhtml*")
    check("M. registro exitoso: vuelve al login con el aviso", "Registro exitoso" in mensaje(nuevo))
    nuevo.context.close()
    nuevo = login(br, email_pac)
    check("M. el usuario registrado es Paciente", nuevo.inner_text(".u-role") == "Paciente")
    nuevo.context.close()

    adm.goto(B + "usuarios.xhtml")
    email_prof = "profesional.%d@mediconecta.test" % sufijo
    # Elegir el rol vuelve a dibujar (AJAX) el panel de matrícula y especialidad, vacío: si ya es Profesional (el
    # valor inicial) no se toca; si no, se espera la respuesta antes de completarlo.
    if adm.eval_on_selector("#formAlta\\:rol", "rol => rol.value") != "PROFESIONAL":
        with adm.expect_response(lambda r: r.request.method == "POST" and "usuarios.xhtml" in r.url):
            adm.select_option("#formAlta\\:rol", "PROFESIONAL")
    adm.wait_for_selector("#formAlta\\:matricula")
    adm.fill("#formAlta\\:nombre", "Dr. Prueba %d" % sufijo)
    adm.fill("#formAlta\\:email", email_prof)
    adm.fill("#formAlta\\:passwordTemporal", "Temporal.2026")
    adm.fill("#formAlta\\:matricula", "MN %d" % sufijo)
    adm.fill("#formAlta\\:especialidad", "Pediatría")
    adm.click("#formAlta button:has-text('Crear usuario')")
    adm.wait_for_load_state()
    fila = adm.locator("#formAlta\\:tablaUsuarios tr, [id$='tablaUsuarios'] tr", has_text=email_prof).first.inner_text()
    check("M. el administrativo crea un profesional con contraseña temporal", "Profesional" in fila and "Temporal" in fila)

    estado, _, _ = http(B + "api/v1/turnos", email_prof, "Temporal.2026")
    check("M. con contraseña temporal el login de la API no entrega token (403)", estado == 403)
    doc = login(br, email_prof, "perfil.xhtml", "Temporal.2026")
    menu = doc.inner_text(".sidebar .nav")
    check("M. con contraseña temporal, el primer ingreso va a Mi perfil y el menú no ofrece nada más",
          "Elegí tu contraseña para continuar" in doc.content() and "Agenda" not in menu
          and doc.locator(".sidebar .user-chip[title='Mi perfil']").count() == 1)
    doc.goto(B + "agenda.xhtml")
    check("M. hasta cambiarla no puede entrar a otra pantalla", "perfil.xhtml" in doc.url)
    doc.fill("#formPassword\\:actual", "Temporal.2026")
    doc.fill("#formPassword\\:nueva", "corta")
    doc.fill("#formPassword\\:repeticion", "corta")
    doc.click("#formPassword button:has-text('Cambiar contraseña')")
    doc.wait_for_load_state()
    check("M. rechaza una contraseña nueva demasiado corta", "al menos 8" in mensaje(doc))
    doc.fill("#formPassword\\:actual", "Temporal.2026")
    doc.fill("#formPassword\\:nueva", "demo1234")
    doc.fill("#formPassword\\:repeticion", "demo1234")
    doc.click("#formPassword button:has-text('Cambiar contraseña')")
    doc.wait_for_url("**/agenda.xhtml*")
    check("M. tras cambiarla entra a su Agenda", "Publicar disponibilidad" in doc.content())
    doc.goto(B + "perfil.xhtml")
    check("M. Mi perfil muestra sus datos (sólo lectura) con la matrícula",
          "MN %d" % sufijo in doc.content() and doc.locator("#formPassword\\:nueva").count() == 1
          and doc.locator("input[value='%s']" % email_prof).count() == 0)
    doc.context.close()
    br.close()

consola(obraSocial="EN_LINEA", autorizaciones="APROBAR", pasarela="EN_LINEA", videoconsulta="EN_LINEA")
print("RESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
