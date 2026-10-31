"""Prueba de punta a punta de la Entrega Obligatoria N.º 2 sobre la app desplegada.

Recorre con un navegador real (y con llamadas HTTP directas) todo lo que se defiende en esta entrega:

  A. Contrato SOAP: el WSDL del legado de la obra social está publicado.
  B. API REST propia: pública, 401 sin credenciales, 200 con HTTP Basic y 404 para un turno ajeno.
  C. Flujo crítico: cotización por SOAP, pago rechazado por la pasarela (el turno sigue en espera) y pago aprobado.
  D. Cola punto a punto: la autorización se resuelve sola, llega la notificación y Facturación genera el reclamo.
  E. Selective Consumer: Facturación figura "no aplica" para TURNO_CONFIRMADO en el monitor.
  F. Falla a mitad del flujo: rollback total y reembolso compensatorio del cobro (visible en la pasarela).
  G. Legado caído: Circuit Breaker abierto, confirmación sin cobro (cobertura pendiente) y la cola reintenta
     hasta que el legado vuelve.
  H. Cancelación: Facturación reintegra el pago (REST) y anula el reclamo, de forma asincrónica.
  I. Telemedicina: el profesional crea la sala por REST y el paciente ve el enlace.
  J. Auditoría por interceptor: el paciente ve qué profesional consultó su historia clínica.
  K. Temas visuales: cambio en tiempo real sin recargar y persistencia al navegar.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados,
Google Chrome y  pip install playwright
Uso:  python pruebas/prueba_entrega4.py
Usa los usuarios de demo (ver SETUP.md). Tarda ~2 minutos: el escenario G espera los reintentos del broker.
"""
import base64, json, random, re, sys, time, urllib.error, urllib.request
from playwright.sync_api import sync_playwright

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "http://localhost:8080/mediconecta/"
EXT = "http://localhost:8080/externos/"
PACIENTE, PROFESIONAL = "juan.perez@mediconecta.test", "ana.lopez@mediconecta.test"
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def http(url, usuario=None, clave=None, metodo="GET", cuerpo=None):
    pedido = urllib.request.Request(url, method=metodo, data=None if cuerpo is None else json.dumps(cuerpo).encode())
    if cuerpo is not None:
        pedido.add_header("Content-Type", "application/json")
    if usuario:
        pedido.add_header("Authorization", "Basic " + base64.b64encode(f"{usuario}:{clave}".encode()).decode())
    try:
        with urllib.request.urlopen(pedido, timeout=10) as r:
            return r.status, r.read().decode("utf-8"), dict(r.headers)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8"), dict(e.headers)


def consola(**cambios):
    return http(EXT + "api/consola/estado", metodo="PUT", cuerpo=cambios)


def login(br, email):
    pg = br.new_context(viewport={"width": 1366, "height": 900}).new_page()
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", "demo1234")
    pg.click("#formLogin button:has-text('Ingresar')")
    pg.wait_for_url("**/agenda.xhtml*")
    return pg


def publicar(prof, fecha, modalidad="PRESENCIAL"):
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha", fecha)
    prof.select_option("#formPublicar\\:modalidad", modalidad)
    prof.click("#formPublicar button:has-text('Publicar')")
    prof.wait_for_load_state()
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
        pac.fill("#formConfirmacion\\:vencimiento", "12/29")
        pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
    pac.click("#formConfirmacion\\:btnConfirmar")
    pac.wait_for_load_state()
    return " ".join(pac.locator(".ui-growl-message").all_inner_texts())


def esperar(pg, url, condicion, segundos):
    fin = time.time() + segundos
    while time.time() < fin:
        pg.goto(url)
        if condicion(pg.content()):
            return True
        time.sleep(3)
    return False


def fecha_al_azar(dia):
    return "%s/12/2026 %02d:%02d" % (dia, random.randint(7, 19), random.choice([5, 15, 25, 35, 45, 55]))


consola(obraSocial="EN_LINEA", autorizaciones="APROBAR", pasarela="EN_LINEA", videoconsulta="EN_LINEA")

# ---- A. SOAP --------------------------------------------------------------------------------
estado, wsdl, _ = http(EXT + "CoberturaService?wsdl")
check("A. WSDL del legado publicado (CoberturaService, operaciones validarCobertura y solicitarAutorizacion)",
      estado == 200 and 'name="CoberturaService"' in wsdl and "solicitarAutorizacion" in wsdl and "validarCobertura" in wsdl)

# ---- B. API REST ----------------------------------------------------------------------------
estado, cuerpo, _ = http(B + "api/v1/profesionales")
check("B. GET /api/v1/profesionales es público y devuelve JSON", estado == 200 and "Ana Lopez" in cuerpo)
estado, _, cabeceras = http(B + "api/v1/turnos")
check("B. GET /api/v1/turnos sin credenciales: 401 con WWW-Authenticate",
      estado == 401 and "Basic" in cabeceras.get("WWW-Authenticate", ""))
estado, _, _ = http(B + "api/v1/turnos", PACIENTE, "incorrecta")
check("B. credenciales inválidas: 401", estado == 401)
estado, cuerpo, _ = http(B + "api/v1/turnos", PACIENTE, "demo1234")
check("B. con HTTP Basic válido: 200 con los turnos del paciente", estado == 200 and cuerpo.startswith("["))
estado, cuerpo, _ = http(B + "api/v1/turnos/999999999", PACIENTE, "demo1234")
check("B. turno inexistente o ajeno: 404 sin trazas", estado == 404 and "no_encontrado" in cuerpo and "Exception" not in cuerpo)

with sync_playwright() as p:
    br = p.chromium.launch(channel="chrome", headless=True)
    prof = login(br, PROFESIONAL)
    pac = login(br, PACIENTE)

    # ---- C. Flujo crítico con pago rechazado y aprobado -----------------------------------
    f1 = fecha_al_azar("01")
    check("C. el profesional publica un horario presencial (" + f1 + ")", publicar(prof, f1))
    reservar(pac, f1)
    contenido = pac.content()
    check("C. la cotización consulta la cobertura por SOAP (OSDE, copago del 30 %)",
          "Consultada en línea al sistema de la obra social (SOAP)" in contenido and "Copago del 30" in contenido)
    msg = pagar(pac, "4000 0000 0000 0002")
    check("C. tarjeta rechazada: se informa y el turno sigue en espera", "Pago rechazado" in msg
          and "Turno reservado temporalmente" in pac.content())
    msg = pagar(pac)
    check("C. tarjeta aprobada: turno confirmado y copago cobrado", "Turno confirmado" in msg and "9600" in msg.replace(".", ""))

    # ---- D. Cola punto a punto + Facturación ---------------------------------------------
    check("D. la autorización de la obra social se resuelve sola por cola.autorizaciones",
          esperar(pac, B + "monitor.xhtml", lambda c: "Autorizada" in c and "AUT-" in c, 30))
    check("D. el paciente recibe la notificación de la autorización",
          esperar(pac, B + "notificaciones.xhtml", lambda c: "autorizó la prestación" in c, 15))
    check("D. Facturación genera el reclamo a la obra social (TURNO_AUTORIZADO)",
          esperar(prof, B + "pagos.xhtml", lambda c: "Pendiente de presentación" in c, 15))

    # ---- E. Selective Consumer ------------------------------------------------------------
    pac.goto(B + "monitor.xhtml")
    filas = pac.locator("#formMonitor\\:tablaEventos_data tr").all_inner_texts()
    confirmacion = next((f for f in filas if "Confirmado" in f), "")
    check("E. Facturación 'no aplica' para TURNO_CONFIRMADO (lo filtra el selector del broker)", "no aplica" in confirmacion)

    # ---- F. Falla a mitad del flujo: rollback + compensación --------------------------------
    pac.goto(B + "integraciones.xhtml")
    pac.click("button:has-text('Armar falla antes del commit')")
    pac.wait_for_load_state()
    check("F. falla de demostración armada", "Falla armada" in pac.content())
    f2 = fecha_al_azar("02")
    publicar(prof, f2)
    pac.goto(B + "agenda.xhtml")
    antes = int(re.search(r"(\d+) turno\(s\) confirmado\(s\)", pac.content()).group(1))
    reservar(pac, f2)
    msg = pagar(pac)
    check("F. la confirmación falla antes del commit y se informa la reversión", "Falla simulada" in msg)
    despues = int(re.search(r"(\d+) turno\(s\) confirmado\(s\)", pac.content()).group(1))
    check("F. el turno NO quedó confirmado (rollback de la base) y sigue en espera para reintentar",
          despues == antes and "Turno reservado temporalmente" in pac.content())
    _, pagos, _ = http(EXT + "api/consola/pagos")
    ultimo = json.loads(pagos)[0]
    check("F. la pasarela muestra el cobro REEMBOLSADO por la compensación (" + ultimo["id"] + ")",
          ultimo["estado"] == "REEMBOLSADO" and "Reversión automática" in (ultimo.get("motivoReembolso") or ""))
    pac.goto(B + "integraciones.xhtml")
    check("F. la bitácora de integraciones registra el reembolso compensatorio (REQUIRES_NEW sobrevive al rollback)",
          "COMPENSACION" in pac.content())
    pac.goto(B + "agenda.xhtml")
    msg = pagar(pac)
    check("F. al reintentar (falla ya desarmada) el turno se confirma", "Turno confirmado" in msg)

    # ---- G. Legado caído: Circuit Breaker + cola con reintentos -----------------------------
    consola(obraSocial="CAIDO")
    f3 = fecha_al_azar("03")
    publicar(prof, f3)
    reservar(pac, f3)
    contenido = pac.content()
    check("G. con el legado caído la cotización usa el fallback (caché o cobertura pendiente)",
          "no respondió" in contenido or "no responde" in contenido)
    msg = pagar(pac)
    check("G. el turno se confirma igual: el resto de la plataforma sigue funcionando", "Turno confirmado" in msg)
    pac.goto(B + "integraciones.xhtml")
    check("G. el Circuit Breaker de la obra social quedó ABIERTO", "ABIERTO" in pac.content())
    check("G. la solicitud de autorización queda en cola mientras el legado no responde",
          esperar(pac, B + "monitor.xhtml", lambda c: "En cola" in c, 15))
    consola(obraSocial="EN_LINEA")
    check("G. cuando el legado vuelve, el broker reentrega y la solicitud se autoriza (sin perderse)",
          esperar(pac, B + "monitor.xhtml",
                  lambda c: "En cola" not in c.split("cola.autorizaciones")[-1], 120))

    # ---- H. Cancelación con reintegro asincrónico -------------------------------------------
    pac.goto(B + "agenda.xhtml")
    pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=f1.replace(" ", " · ")).locator("button:has-text('Cancelar')").click()
    pac.wait_for_load_state()
    check("H. cancelación aceptada, con aviso del reintegro", "reintegro" in pac.content())
    check("H. Facturación reintegra el pago por REST (estado Reembolsado)",
          esperar(pac, B + "pagos.xhtml", lambda c: "Reembolsado" in c, 20))
    check("H. y anula el reclamo a la obra social", esperar(prof, B + "pagos.xhtml", lambda c: "Anulado" in c, 15))

    # ---- I. Telemedicina -------------------------------------------------------------------
    f4 = fecha_al_azar("04")
    publicar(prof, f4, "TELEMEDICINA")
    reservar(pac, f4)
    msg = pagar(pac)
    check("I. teleconsulta confirmada (precio con descuento de telemedicina)", "Turno confirmado" in msg)
    prof.goto(B + "telemedicina.xhtml")
    prof.locator("#formTelemedicina\\:tablaTeleconsultas tr", has_text=f4.split(" ")[0]).locator(
        "button:has-text('Iniciar videoconsulta')").first.click()
    prof.wait_for_load_state()
    check("I. el profesional crea la sala en el proveedor externo (REST)", "Sala creada" in prof.content()
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
    br.close()

consola(obraSocial="EN_LINEA", autorizaciones="APROBAR", pasarela="EN_LINEA", videoconsulta="EN_LINEA")
print("RESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
