"""Prueba de la cancelación de turnos por el paciente, el profesional y el administrativo, sobre la app desplegada.

  A. El profesional cancela desde su Agenda (con confirmación; "No" no cancela): avisos al paciente ("Tu profesional
     canceló el turno") y al profesional, reintegro automático y el horario NO se vuelve a ofrecer.
  B. El administrativo cancela desde la pantalla Turnos (filtrada por profesional, de a tandas): avisos que dicen
     que canceló la administración, reintegro y el horario NO se vuelve a ofrecer. Sólo el administrativo tiene
     esa pantalla.
  C. Control: cuando cancela el paciente, el horario SÍ se vuelve a ofrecer.
  D. API REST: el profesional cancela un turno de su agenda (200, y 409 si repite), un paciente ajeno recibe 404,
     el administrativo cancela cualquiera (200) y sigue sin poder listar turnos (403).
  E. D-13: un turno que ya empezó no se cancela. Se publica uno para dentro de 2 minutos, el paciente lo confirma y
     lo paga, y pasada la hora sale de las agendas, la API responde 409 a los tres roles y el pago no se reintegra.
     (Tarda unos 2 minutos más que el resto: hay que esperar a que llegue la hora del turno.)
  F. D-36: el paciente y la profesional cancelan el mismo turno por la API al mismo tiempo: uno gana (200) y el otro
     recibe 409; nunca un error 500 (antes el bloqueo optimista hacía fallar el commit del segundo).
  G. Eje C, ETag: el GET de un turno trae su ETag (y 304 con If-None-Match). La profesional y el paciente leen la
     misma; ella cancela con If-Match (200 y ETag nueva) y el paciente, con la ETag vieja, recibe 412 y nada cambia.
  H. Eje C, Idempotency-Key: el reintento con la misma clave recibe exactamente la misma respuesta (marcada
     Idempotent-Replayed) en lugar de un 409; dos pedidos simultáneos con la misma clave cancelan una sola vez; la
     misma clave en otro pedido da 422, y una clave con formato inválido, 400.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados, Google
Chrome (o Edge con NAVEGADOR=msedge) y  pip install playwright
Uso:  python pruebas/prueba_cancelacion.py
Deja en la base un paciente de prueba por corrida (cancelacion.NNNNN@mediconecta.test) y sus turnos cancelados con
la Dra. Ana Lopez (más el horario que vuelve a ofrecerse en C y el turno ya pasado y confirmado de E).
"""
import json, os, random, ssl, sys, time, urllib.error, urllib.request, uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta
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
PROFESIONAL, ADMINISTRATIVO, OTRO_PACIENTE = ("ana.lopez@mediconecta.test", "carla.admin@mediconecta.test",
                                             "juan.perez@mediconecta.test")
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def api(ruta, metodo="GET", cuerpo=None, token=None):
    pedido = urllib.request.Request(B + "api/v1/" + ruta, method=metodo,
                                    data=None if cuerpo is None else json.dumps(cuerpo).encode())
    pedido.add_header("Content-Type", "application/json")
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(pedido, timeout=15) as r:
            return r.status, json.loads(r.read().decode() or "null")
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or "null")


def api_completo(ruta, metodo="GET", token=None, cabeceras=None):
    """Pedido a la API con cabeceras propias: (estado, cabeceras de la respuesta, cuerpo como texto)."""
    pedido = urllib.request.Request(B + "api/v1/" + ruta, method=metodo, data=b"" if metodo == "POST" else None)
    pedido.add_header("Content-Type", "application/json")
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    for nombre, valor in (cabeceras or {}).items():
        pedido.add_header(nombre, valor)
    try:
        with urllib.request.urlopen(pedido, timeout=15) as r:
            return r.status, dict(r.headers), r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, dict(e.headers), e.read().decode()


def token(email):
    return api("auth/login", "POST", {"email": email, "password": "demo1234"})[1]["token"]


def consola(**modos):
    pedido = urllib.request.Request(EXT + "api/consola/estado", method="PUT", data=json.dumps(modos).encode(),
                                    headers={"Content-Type": "application/json"})
    urllib.request.urlopen(pedido, timeout=10)


def login(br, email, clave="demo1234"):
    pg = br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", clave)
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg


def mensaje(pg):
    return " ".join(pg.locator(".ui-growl-message").all_inner_texts())


def dia_desde_hoy(dias):
    """Un día a tantos días de hoy ("dd/mm/aaaa"): la prueba no depende de la fecha en que se corre."""
    return (date.today() + timedelta(days=dias)).strftime("%d/%m/%Y")


def fecha_al_azar():
    """Un horario a 26-31 días de hoy, lejos de los días que usan las otras pruebas."""
    return "%s %02d:%02d" % (dia_desde_hoy(random.randint(26, 31)), random.randint(7, 19), random.randint(0, 11) * 5)


def publicar(prof, fecha):
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", fecha)
    prof.keyboard.press("Escape")
    with prof.expect_navigation():
        prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
    return "Disponibilidad publicada" in mensaje(prof)


def publicar_libre(prof):
    """Un horario libre publicado: si ya existe uno igual de otra corrida (D-23 no lo deja repetir), elige otro."""
    for _ in range(15):
        fecha = fecha_al_azar()
        if publicar(prof, fecha):
            return fecha
    raise AssertionError("no se encontró un horario libre a 26-31 días de hoy")


def ofrecido(pac, fecha):
    """¿El horario figura entre los disponibles de la Dra. Ana Lopez?"""
    dia, hora = fecha.split(" ")
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    return pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).count() > 0


def reservar_y_pagar(pac, fecha):
    dia, hora = fecha.split(" ")
    if not ofrecido(pac, fecha):
        # Pasó una vez en el pipeline y no se pudo reproducir: si vuelve a pasar, que diga qué había en pantalla.
        disponibles = [" ".join(t.split()) for t in pac.locator("#formAgenda\\:tablaTurnos tr").all_inner_texts()]
        raise AssertionError("el horario %s no figura entre los disponibles de la Dra. Ana Lopez (página %s). "
                             "Disponibles: %s. Mensajes: %s" % (fecha, pac.url.split("/")[-1], disponibles[1:9] or "ninguno",
                                                               mensaje(pac) or "ninguno"))
    with pac.expect_navigation():
        pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Reservar')").first.click()
    pac.fill("#formConfirmacion\\:titular", "Paciente Cancelacion")
    pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
    pac.fill("#formConfirmacion\\:vencimiento", ""); pac.fill("#formConfirmacion\\:vencimiento", "12/29")
    pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
    pac.click("#formConfirmacion\\:btnConfirmar")
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    return "Turno confirmado" in pac.content()


def esperar(pg, url, condicion, segundos=25):
    fin = time.time() + segundos
    while True:
        pg.goto(url)
        if condicion(pg.content()) or time.time() > fin:
            return condicion(pg.content())
        time.sleep(2)


def reembolsado(pac, fecha):
    def fila(contenido):
        filas = pac.locator("[id$='tablaPagos'] tr", has_text=fecha)
        return filas.count() > 0 and "Reembolsado" in filas.first.inner_text()
    return esperar(pac, B + "pagos.xhtml", lambda c: fila(c))


def turno_id(token_paciente, fecha):
    dia, hora = fecha.split(" ")
    d, m, a = dia.split("/")
    iso = "%s-%s-%sT%s" % (a, m, d, hora)
    turnos = api("turnos", token=token_paciente)[1]
    return next(t["id"] for t in turnos if t["fechaHora"].startswith(iso) and t["estado"] == "CONFIRMADO")


with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR")
    sufijo = random.randint(10000, 99999)
    email, nombre = "cancelacion.%d@mediconecta.test" % sufijo, "Paciente Cancelacion %d" % sufijo
    nuevo = br.new_context(ignore_https_errors=True).new_page()
    nuevo.goto(B + "registro.xhtml")
    for campo, valor in (("nombre", nombre), ("email", email), ("password", "demo1234"), ("repeticion", "demo1234")):
        nuevo.fill("#formRegistro\\:" + campo, valor)
    with nuevo.expect_navigation():
        nuevo.click("#formRegistro button:has-text('Registrarme')")
    nuevo.context.close()

    prof = login(br, PROFESIONAL)
    f1, f2, f3, f4, f5 = sorted(publicar_libre(prof) for _ in range(5))
    pac = login(br, email)

    # ---- A. Cancela el profesional desde su Agenda ----------------------------------------------
    print("-- A. Cancela el profesional")
    check("A. el paciente confirma y paga un turno (%s)" % f1, reservar_y_pagar(pac, f1))
    prof.goto(B + "agenda.xhtml")
    fila = prof.locator("#formTurnosProfesional\\:tablaConfirmadosProfesional tr", has_text=f1)
    fila.locator("button:has-text('Cancelar')").click()
    dialogo = prof.locator(".ui-confirm-dialog:visible")
    dialogo.wait_for()
    check("A. pide confirmación nombrando al paciente y la fecha", nombre in dialogo.inner_text() and f1 in dialogo.inner_text())
    dialogo.locator("button.ui-confirmdialog-no").click()
    prof.goto(B + "agenda.xhtml")
    check("A. con «No» el turno sigue confirmado",
          prof.locator("#formTurnosProfesional\\:tablaConfirmadosProfesional tr", has_text=f1).count() == 1)
    prof.locator("#formTurnosProfesional\\:tablaConfirmadosProfesional tr", has_text=f1).locator("button:has-text('Cancelar')").click()
    with prof.expect_navigation():
        prof.locator(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')").click()
    check("A. la profesional cancela y ve la confirmación", "Cancelaste el turno de " + nombre in mensaje(prof))
    check("A. el turno ya no figura entre sus confirmados",
          prof.locator("#formTurnosProfesional\\:tablaConfirmadosProfesional tr", has_text=f1).count() == 0)
    check("A. el paciente recibe el aviso de que su profesional canceló",
          esperar(pac, B + "notificaciones.xhtml", lambda c: "Tu profesional canceló el turno" in c and f1 in c))
    check("A. la profesional tiene el aviso en su bandeja",
          esperar(prof, B + "notificaciones.xhtml", lambda c: "Cancelaste el turno de " + nombre + " del " + f1 in c))
    check("A. el pago se reintegra solo", reembolsado(pac, f1))
    check("A. el horario NO se vuelve a ofrecer", not ofrecido(pac, f1))

    # ---- B. Cancela el administrativo desde Turnos ---------------------------------------------
    print("-- B. Cancela el administrativo")
    check("B. el paciente confirma y paga otro turno (%s)" % f2, reservar_y_pagar(pac, f2))
    adm = login(br, ADMINISTRATIVO, )
    check("B. el administrativo tiene la pantalla Turnos en el menú", adm.locator("a.nav-item", has_text="Turnos").count() == 1)
    adm.goto(B + "turnos.xhtml")
    with adm.expect_response(lambda r: "turnos.xhtml" in r.url and r.request.method == "POST"):
        adm.select_option("#formTurnos\\:profesional",
                          label=adm.locator("#formTurnos\\:profesional option", has_text="Ana Lopez").first.inner_text())
    filas = adm.locator("#formTurnos\\:tablaTurnos_data tr").all_inner_texts()
    check("B. filtrada por la Dra. Ana Lopez, carga de a tandas (%d filas en la página)" % len(filas),
          0 < len(filas) <= 10 and all("Ana Lopez" in f for f in filas))
    # El turno de f2 puede no estar en la primera página: se avanza (por AJAX) hasta encontrarlo, esperando en cada
    # paso que cambie el "1–10 de N" del paginador.
    reporte = "#formTurnos\\:tablaTurnos .ui-paginator-current"
    for _ in range(20):
        if adm.locator("#formTurnos\\:tablaTurnos tr", has_text=nombre).filter(has_text=f2).count():
            break
        siguiente = adm.locator("#formTurnos\\:tablaTurnos .ui-paginator-next").first
        if "ui-state-disabled" in (siguiente.get_attribute("class") or ""):
            break
        antes = adm.locator(reporte).first.inner_text()
        siguiente.click()
        adm.wait_for_function("([sel, antes]) => document.querySelector(sel).innerText !== antes",
                              arg=[reporte, antes])
    fila = adm.locator("#formTurnos\\:tablaTurnos tr", has_text=nombre).filter(has_text=f2)
    check("B. encuentra el turno del paciente", fila.count() == 1)
    fila.locator("button:has-text('Cancelar')").click()
    with adm.expect_navigation():
        adm.locator(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')").click()
    check("B. el administrativo cancela y ve la confirmación", "Cancelaste el turno de " + nombre + " con Dra. Ana Lopez" in mensaje(adm))
    check("B. el paciente recibe el aviso de que canceló la administración",
          esperar(pac, B + "notificaciones.xhtml", lambda c: "la administración de MediConecta canceló tu turno" in c and f2 in c))
    check("B. la profesional también recibe el aviso",
          esperar(prof, B + "notificaciones.xhtml", lambda c: "La administración canceló el turno de " + nombre + " del " + f2 in c))
    check("B. el pago se reintegra solo", reembolsado(pac, f2))
    check("B. el horario NO se vuelve a ofrecer", not ofrecido(pac, f2))
    pac.goto(B + "turnos.xhtml")
    check("B. un paciente no tiene la pantalla Turnos (vuelve a su Agenda)", "agenda.xhtml" in pac.url)
    prof.goto(B + "turnos.xhtml")
    check("B. un profesional tampoco", "agenda.xhtml" in prof.url)
    adm.context.close()

    # ---- C. Control: cancela el paciente -------------------------------------------------------
    print("-- C. Cancela el paciente")
    check("C. el paciente confirma un turno (%s)" % f3, reservar_y_pagar(pac, f3))
    pac.goto(B + "agenda.xhtml")
    pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=f3.replace(" ", " · ")).locator("button:has-text('Cancelar')").first.click()
    dialogo = pac.locator(".ui-confirm-dialog:visible")
    dialogo.wait_for()
    check("C. el paciente también confirma, y el diálogo nombra a la profesional y la fecha (D-21)",
          "Ana Lopez" in dialogo.inner_text() and f3 in dialogo.inner_text())
    with pac.expect_navigation():
        pac.locator(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')").click()
    check("C. el paciente cancela", "Turno cancelado" in mensaje(pac))
    check("C. cuando cancela el paciente, el horario SÍ se vuelve a ofrecer", ofrecido(pac, f3))

    # ---- D. API REST -----------------------------------------------------------------------------
    print("-- D. API REST")
    check("D. el paciente confirma dos turnos más (%s y %s)" % (f4, f5), reservar_y_pagar(pac, f4) and reservar_y_pagar(pac, f5))
    tk_pac, tk_prof, tk_adm, tk_otro = token(email), token(PROFESIONAL), token(ADMINISTRATIVO), token(OTRO_PACIENTE)
    id4, id5 = turno_id(tk_pac, f4), turno_id(tk_pac, f5)
    estado, cuerpo = api("turnos/%d/cancelacion" % id4, "POST", token=tk_otro)
    check("D. otro paciente no puede cancelarlo: 404, como si no existiera (%d)" % estado, estado == 404)
    estado, cuerpo = api("turnos/%d/cancelacion" % id4, "POST", token=tk_prof)
    check("D. la profesional lo cancela por la API: 200 y CANCELADO (%d)" % estado, estado == 200 and cuerpo["estado"] == "CANCELADO")
    estado, cuerpo = api("turnos/%d/cancelacion" % id4, "POST", token=tk_prof)
    check("D. si lo repite: 409 (patrón State) (%d)" % estado, estado == 409 and cuerpo["error"] == "transicion_invalida")
    estado, cuerpo = api("turnos/%d/cancelacion" % id5, "POST", token=tk_adm)
    check("D. el administrativo cancela cualquiera por la API: 200 (%d)" % estado, estado == 200 and cuerpo["estado"] == "CANCELADO")
    check("D. y no recibe el enlace a la sala de videoconsulta", cuerpo.get("salaVideoconsulta") in (None, ""))
    check("D. el administrativo sigue sin poder listar turnos por la API: 403", api("turnos", token=tk_adm)[0] == 403)
    check("D. los horarios cancelados por la API tampoco se vuelven a ofrecer", not ofrecido(pac, f4) and not ofrecido(pac, f5))

    # ---- E. D-13: un turno que ya empezó no se cancela -------------------------------------------
    print("-- E. Un turno que ya empezó no se cancela (D-13)")
    # Los horarios van de 5 en 5 minutos (D-39): el primero que deje al menos 2 minutos para publicarlo,
    # reservarlo y pagarlo antes de que empiece.
    inicio = (datetime.now() + timedelta(minutes=3)).replace(second=0, microsecond=0)
    inicio += timedelta(minutes=(-inicio.minute) % 5)
    f6 = inicio.strftime("%d/%m/%Y %H:%M")
    publicar(prof, f6)
    check("E. el paciente confirma y paga un turno que empieza a las %s" % f6[-5:], reservar_y_pagar(pac, f6))
    id6 = turno_id(tk_pac, f6)

    def en_agenda(pg, tabla):
        """Cuántas veces figura el turno de E en la tabla; None si la página no es la Agenda (un error o el login),
        para que "ya no figura" no se dé por bueno con una página que no cargó (así se escondió el D-43)."""
        if pg.locator("#formAgenda\\:profesional, #formPublicar").count() == 0:
            return None
        return pg.locator(tabla + " tr", has_text=f6.replace(" ", " · ") if "MisTurnos" in tabla else f6).count()

    pac.goto(B + "agenda.xhtml")
    check("E. antes de la hora figura en los próximos turnos del paciente", en_agenda(pac, "#formAgenda\\:tablaMisTurnos") == 1)
    time.sleep(max(0.0, (inicio - datetime.now()).total_seconds()) + 3)
    pac.goto(B + "agenda.xhtml")
    check("E. pasada la hora sale de la agenda del paciente", en_agenda(pac, "#formAgenda\\:tablaMisTurnos") == 0)
    prof.goto(B + "agenda.xhtml")
    check("E. y de la agenda de la profesional", en_agenda(prof, "#formTurnosProfesional\\:tablaConfirmadosProfesional") == 0)
    for quien, tk in (("el paciente", tk_pac), ("la profesional", tk_prof), ("el administrativo", tk_adm)):
        estado, cuerpo = api("turnos/%d/cancelacion" % id6, "POST", token=tk)
        check("E. %s intenta cancelarlo por la API: 409 (%d, %s)" % (quien, estado, (cuerpo or {}).get("mensaje")),
              estado == 409 and "ya empezó" in cuerpo["mensaje"])
    estado, cuerpo = api("turnos/%d" % id6, token=tk_pac)
    check("E. el turno sigue CONFIRMADO (%s)" % (cuerpo or {}).get("estado"), estado == 200 and cuerpo["estado"] == "CONFIRMADO")
    time.sleep(5)
    pac.goto(B + "pagos.xhtml")
    fila6 = pac.locator("[id$='tablaPagos'] tr", has_text=f6)
    check("E. el pago sigue cobrado: no hubo reintegro", fila6.count() > 0 and "Reembolsado" not in fila6.first.inner_text())

    # ---- F. D-36: dos cancelaciones simultáneas ----------------------------------------------------
    print("-- F. Dos cancelaciones al mismo tiempo (D-36)")
    for vuelta in (1, 2):
        f7 = publicar_libre(prof)
        check("F%d. el paciente confirma un turno (%s)" % (vuelta, f7), reservar_y_pagar(pac, f7))
        id7 = turno_id(tk_pac, f7)
        with ThreadPoolExecutor(2) as pool:
            respuestas = list(pool.map(lambda tk: api("turnos/%d/cancelacion" % id7, "POST", token=tk), (tk_pac, tk_prof)))
        estados = sorted(estado for estado, _ in respuestas)
        check("F%d. el paciente y la profesional cancelan a la vez: uno gana y el otro recibe 409, nunca 500 (%s)"
              % (vuelta, estados), estados == [200, 409])

    # ---- G. ETag e If-Match: actualización concurrente con una ETag desactualizada (Eje C) -------------
    print("-- G. ETag e If-Match")
    f8 = publicar_libre(prof)
    check("G. el paciente confirma un turno (%s)" % f8, reservar_y_pagar(pac, f8))
    id8 = turno_id(tk_pac, f8)
    estado, cabeceras, _ = api_completo("turnos/%d" % id8, token=tk_pac)
    etag = cabeceras.get("ETag")
    check("G. el GET del turno trae su ETag (%s)" % etag, estado == 200 and bool(etag) and etag.startswith('"'))
    estado, _, cuerpo = api_completo("turnos/%d" % id8, token=tk_pac, cabeceras={"If-None-Match": etag})
    check("G. con If-None-Match y la misma ETag: 304, sin cuerpo (%d)" % estado, estado == 304 and not cuerpo)
    estado_prof, cabeceras_prof, _ = api_completo("turnos/%d" % id8, token=tk_prof)
    check("G. la profesional lee la misma ETag que el paciente", estado_prof == 200 and cabeceras_prof.get("ETag") == etag)
    estado, cabeceras, cuerpo = api_completo("turnos/%d/cancelacion" % id8, "POST", token=tk_prof, cabeceras={"If-Match": etag})
    check("G. la profesional cancela con If-Match: 200, CANCELADO y una ETag nueva (%d, %s)" % (estado, cabeceras.get("ETag")),
          estado == 200 and json.loads(cuerpo)["estado"] == "CANCELADO" and cabeceras.get("ETag") not in (None, etag))
    estado, _, cuerpo = api_completo("turnos/%d/cancelacion" % id8, "POST", token=tk_pac, cabeceras={"If-Match": etag})
    check("G. el paciente, con la ETag vieja: 412 precondicion_fallida, no 409 (%d %s)" % (estado, json.loads(cuerpo).get("error")),
          estado == 412 and json.loads(cuerpo).get("error") == "precondicion_fallida")

    # ---- H. Idempotency-Key: el reintento recibe la misma respuesta (Eje C) ----------------------------
    print("-- H. Idempotency-Key")
    f9 = publicar_libre(prof)
    check("H. el paciente confirma un turno (%s)" % f9, reservar_y_pagar(pac, f9))
    id9 = turno_id(tk_pac, f9)
    clave = str(uuid.uuid4())
    primera = api_completo("turnos/%d/cancelacion" % id9, "POST", token=tk_pac, cabeceras={"Idempotency-Key": clave})
    check("H. el primer pedido con la clave cancela (%d)" % primera[0], primera[0] == 200)
    segunda = api_completo("turnos/%d/cancelacion" % id9, "POST", token=tk_pac, cabeceras={"Idempotency-Key": clave})
    check("H. el reintento con la misma clave recibe exactamente la misma respuesta, marcada como repetida, y no un 409 (%d)"
          % segunda[0], segunda[0] == 200 and segunda[2] == primera[2] and segunda[1].get("Idempotent-Replayed") == "true"
          and segunda[1].get("ETag") == primera[1].get("ETag"))
    sin_clave = api("turnos/%d/cancelacion" % id9, "POST", token=tk_pac)
    check("H. sin la clave, el mismo pedido es una segunda cancelación: 409 (%d)" % sin_clave[0], sin_clave[0] == 409)
    otra = api_completo("turnos/%d/cancelacion" % id8, "POST", token=tk_pac, cabeceras={"Idempotency-Key": clave})
    check("H. la misma clave en otro pedido: 422 clave_reutilizada (%d)" % otra[0],
          otra[0] == 422 and json.loads(otra[2]).get("error") == "clave_reutilizada")
    mala = api_completo("turnos/%d/cancelacion" % id9, "POST", token=tk_pac, cabeceras={"Idempotency-Key": "dos palabras"})
    check("H. una clave con formato inválido: 400 (%d)" % mala[0], mala[0] == 400)
    check("H. el pago se reintegró (una sola cancelación)", reembolsado(pac, f9))

    f10 = publicar_libre(prof)
    check("H. el paciente confirma otro turno (%s)" % f10, reservar_y_pagar(pac, f10))
    id10 = turno_id(tk_pac, f10)
    clave2 = str(uuid.uuid4())
    with ThreadPoolExecutor(2) as pool:
        juntas = list(pool.map(lambda _: api_completo("turnos/%d/cancelacion" % id10, "POST", token=tk_pac,
                                                      cabeceras={"Idempotency-Key": clave2}), range(2)))
    repetidas = sum(1 for r in juntas if r[1].get("Idempotent-Replayed") == "true")
    check("H. dos pedidos simultáneos con la misma clave: los dos 200 con la misma respuesta y uno solo hizo la "
          "cancelación (%s, %d repetida)" % ([r[0] for r in juntas], repetidas),
          [r[0] for r in juntas] == [200, 200] and juntas[0][2] == juntas[1][2] and repetidas == 1)

    prof.context.close()
    pac.context.close()
    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
