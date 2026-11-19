"""Regresión de los defectos de la prueba exploratoria del 17/11, sobre la app desplegada.

Cada escenario lleva el id del defecto, que es también el de su tarjeta en el tablero. D-13 (no se cancela un turno
que ya empezó) y D-21 (el paciente confirma antes de cancelar) están en prueba_cancelacion.py, escenarios E y C.

  D-20. Con la obra social caída la cobertura queda pendiente y aparece "Consultar de nuevo a tu obra social", que
        siempre informa el resultado; cuando la obra social vuelve, la cobertura se completa y el botón desaparece.
  D-23. No se puede publicar dos veces el mismo horario, ni siquiera con doble clic.
  D-17. En la Agenda del paciente la búsqueda está antes que "Mis turnos".
  D-18. Los días de la semana se muestran en español.
  D-30. El vencimiento de la tarjeta se completa con la barra (0228 → 02/28).
  D-19. Con datos de tarjeta incompletos o mal escritos aparece un único mensaje con todo lo que hay que corregir.
  D-29. Antes de cobrar, un diálogo muestra el importe y la tarjeta; "Revisar los datos" no cobra.
  D-15. En Historia Clínica el profesional ve a cada paciente una sola vez, aunque tenga varios turnos.
  D-32. La sección de auditoría del paciente se explica sin jerga técnica.
  D-16. Se puede marcar una sola notificación como leída, y el contador del menú baja.
  D-14, D-31, D-25, D-26 y micrófono. El profesional abre la sala de una teleconsulta: al paciente le llega el aviso;
        Telemedicina no muestra el id interno; la sala no tiene notas técnicas y tiene micrófono con «Silenciar». Al
        finalizar la consulta, la sala se cierra para los dos y la teleconsulta sale de las listas.
  D-24, D-27 y D-28. Sin jerga técnica en las pantallas del paciente, el contenido aprovecha un monitor ancho y el
        perfil se abre desde la tarjeta del usuario (no se repite en el menú).
  D-36. Dos pacientes reservan el mismo horario al mismo tiempo: uno se lo queda y al otro se le dice que ya no está
        disponible; ninguno ve un error.
  D-36 (paso 1). El paciente cancela mientras otra transacción modifica el mismo turno, como la autorización de la
        obra social: la cancelación la espera y cancela sobre la versión nueva, sin error 500. Necesita WILDFLY_HOME
        (lee el datasource de MediConecta y usa el driver de PostgreSQL de WildFly) y Java; sin WILDFLY_HOME se omite.
  D-38. La pantalla Pagos del administrativo (su página de inicio) abre en pocos segundos aunque haya cientos de
        cobros y reclamos: cada listado sale en una sola consulta, no una por fila.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados, Google
Chrome (o Edge con NAVEGADOR=msedge) y  pip install playwright
Uso:  python pruebas/prueba_defectos.py
Deja en la base un paciente de prueba por corrida (defectos.NNNNN@mediconecta.test, OSDE) con dos turnos confirmados
(y uno cancelado) y los horarios publicados a 33-39 días de hoy con la Dra. Ana Lopez. Tarda unos 2 minutos: en D-20 espera que se cierre el
Circuit Breaker de la obra social.
"""
import glob, json, os, random, re, ssl, subprocess, sys, time, urllib.error, urllib.request
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
BLOQUEO = os.path.join(os.path.dirname(os.path.abspath(__file__)), "BloqueoExterno.java")
DIAS = ("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")
DAYS = ("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def consola(**modos):
    pedido = urllib.request.Request(EXT + "api/consola/estado", method="PUT", data=json.dumps(modos).encode(),
                                    headers={"Content-Type": "application/json"})
    urllib.request.urlopen(pedido, timeout=10)


def api(ruta, metodo="GET", cuerpo=None, token=None):
    pedido = urllib.request.Request(B + "api/v1/" + ruta, method=metodo,
                                    data=(b"" if metodo == "POST" else None) if cuerpo is None else json.dumps(cuerpo).encode())
    pedido.add_header("Content-Type", "application/json")
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(pedido, timeout=30) as respuesta:
            return respuesta.status, json.loads(respuesta.read().decode() or "null")
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read().decode() or "null")


def datasource_de_wildfly():
    """URL, usuario y contraseña del datasource de MediConecta, leídos de la configuración de WildFly (no se imprimen)."""
    try:
        with open(os.path.join(WILDFLY_HOME, "standalone", "configuration", "standalone-full.xml"), encoding="utf-8") as xml:
            configuracion = xml.read()
        bloque = configuracion[configuracion.index('jndi-name="java:/mediconectaDS"'):]
    except (OSError, ValueError):
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


def dia_desde_hoy(dias):
    """Un día a tantos días de hoy ("dd/mm/aaaa"): la prueba no depende de la fecha en que se corre."""
    return (date.today() + timedelta(days=dias)).strftime("%d/%m/%Y")


def fecha_al_azar():
    """Un horario a 33-39 días de hoy, lejos de los días que usan las otras pruebas."""
    return "%s %02d:%02d" % (dia_desde_hoy(random.randint(33, 39)), random.randint(7, 19), random.randint(0, 59))


def publicar_libre(prof, modalidad="Presencial"):
    """Un horario libre publicado: si ya existe uno igual de otra corrida (D-23 no lo deja repetir), elige otro."""
    for _ in range(15):
        fecha = fecha_al_azar()
        if "Disponibilidad publicada" in publicar(prof, fecha, modalidad):
            return fecha
    raise AssertionError("no se encontró un horario libre en diciembre")


def publicar(prof, fecha, modalidad="Presencial"):
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", fecha)
    prof.keyboard.press("Escape")
    prof.select_option("#formPublicar\\:modalidad", label=modalidad)
    with prof.expect_navigation():
        prof.locator("#formPublicar button:has-text('Publicar')").evaluate("b => b.click()")
    return " ".join(mensajes(prof))


def publicar_dos_veces_a_la_vez(prof, fecha):
    """Simula el doble clic: dos envíos del mismo formulario en paralelo. Devuelve los códigos HTTP."""
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", fecha)
    prof.keyboard.press("Escape")
    return prof.locator("#formPublicar button:has-text('Publicar')").evaluate("""async b => {
        const datos = new FormData(b.form);
        datos.append(b.name, b.name);
        return Promise.all([1, 2].map(() =>
            fetch(b.form.action, {method: 'POST', body: new URLSearchParams(datos)})
                .then(async r => [r.status, (await r.text()).includes('Disponibilidad publicada')])));
    }""")


def buscar(pac):
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")


def filas_disponibles(pac, fecha):
    dia, hora = fecha.split(" ")
    return pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora)


def reservar(pac, fecha):
    buscar(pac)
    with pac.expect_navigation():
        filas_disponibles(pac, fecha).locator("button:has-text('Reservar')").first.click()


def tarjeta(pac, titular="Paciente Defectos", numero="4111 1111 1111 1111", vencimiento="12/29", codigo="123"):
    pac.fill("#formConfirmacion\\:titular", titular)
    pac.fill("#formConfirmacion\\:numeroTarjeta", numero)
    pac.fill("#formConfirmacion\\:vencimiento", "")  # la máscara mezcla lo nuevo con lo anterior
    pac.fill("#formConfirmacion\\:vencimiento", vencimiento)
    pac.fill("#formConfirmacion\\:codigoSeguridad", codigo)


def pagar(pac):
    pac.click("#formConfirmacion\\:btnConfirmar")
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    return " ".join(mensajes(pac))


def revisar_tarjeta(pac):
    """Primer paso del pago: devuelve los mensajes nuevos que dejó (espera la respuesta AJAX)."""
    with pac.expect_response(lambda r: "agenda.xhtml" in r.url and r.request.method == "POST"):
        pac.click("#formConfirmacion\\:btnConfirmar")
    pac.wait_for_timeout(400)
    return mensajes(pac)


with sync_playwright() as p:
    # Cámara y micrófono simulados por el navegador, sin pedir permiso (la sala de videoconsulta los usa).
    br = p.chromium.launch(channel=NAVEGADOR, headless=True,
                           args=["--use-fake-ui-for-media-stream", "--use-fake-device-for-media-stream"])
    consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR")
    sufijo = random.randint(10000, 99999)
    email, nombre = "defectos.%d@mediconecta.test" % sufijo, "Paciente Defectos %d" % sufijo
    nuevo = pagina(br)
    nuevo.goto(B + "registro.xhtml")
    for campo, valor in (("nombre", nombre), ("email", email), ("password", "demo1234"), ("repeticion", "demo1234"),
                         ("obraSocial", "OSDE 210"), ("nroAfiliado", str(sufijo))):
        nuevo.fill("#formRegistro\\:" + campo, valor)
    with nuevo.expect_navigation():
        nuevo.click("#formRegistro button:has-text('Registrarme')")
    nuevo.context.close()

    prof = login(br, PROFESIONAL)
    pac = login(br, email)

    # ---- D-20. Consultar de nuevo a la obra social -------------------------------------------------
    # Va primero: el paciente todavía no tiene ninguna cobertura en la caché, así que con el legado caído queda pendiente.
    print("-- D-20. Consultar de nuevo a la obra social")
    f3 = publicar_libre(prof)
    try:
        consola(obraSocial="CAIDO")
        reservar(pac, f3)
        boton = pac.locator("button:has-text('Consultar de nuevo a tu obra social')")
        check("D-20. con la obra social caída la cobertura queda pendiente y se ofrece consultar de nuevo",
              "Pendiente" in pac.inner_text("#formConfirmacion") and boton.count() == 1)
        with pac.expect_navigation():
            boton.click()
        check("D-20. si sigue sin responder, lo dice (no parece que el botón no hace nada)",
              any("todavía no responde" in m for m in mensajes(pac)))
        consola(obraSocial="EN_LINEA")
        time.sleep(35)  # el Circuit Breaker queda abierto 30 s después de las fallas
        with pac.expect_navigation():
            pac.locator("button:has-text('Consultar de nuevo a tu obra social')").click()
        check("D-20. cuando la obra social vuelve, informa que actualizó la cobertura",
              any("actualizamos la cobertura" in m for m in mensajes(pac)))
        check("D-20. la cobertura queda Cubierta y el botón desaparece",
              "Cubierta" in pac.inner_text("#formConfirmacion")
              and pac.locator("button:has-text('Consultar de nuevo a tu obra social')").count() == 0)
        check("D-24. la cobertura se explica sin «SOAP» ni «caché», ni siquiera en el código de la página",
              "Consultada en línea a tu obra social" in pac.inner_text("#formConfirmacion")
              and "SOAP" not in pac.content() and "caché" not in pac.content())
        with pac.expect_navigation():
            pac.click("#formConfirmacion button:has-text('Liberar turno')")
    finally:
        consola(obraSocial="EN_LINEA")

    # ---- D-23. Sin horarios repetidos ---------------------------------------------------------------
    print("-- D-23. Sin horarios repetidos")
    f1 = publicar_libre(prof)
    check("D-23. la profesional publica un horario (%s)" % f1, True)
    dia, hora = f1.split(" ")
    repetido = publicar(prof, f1)
    check("D-23. si lo publica otra vez, se rechaza con un mensaje claro",
          "Ya tenés publicado un horario el %s a las %s hs." % (dia, hora) in repetido)
    buscar(pac)
    check("D-23. el paciente ve ese horario una sola vez", filas_disponibles(pac, f1).count() == 1)
    for _ in range(15):  # un horario libre: si las dos respuestas dicen que ya existía, era de otra corrida
        f2 = fecha_al_azar()
        respuestas = publicar_dos_veces_a_la_vez(prof, f2)
        if any(publicado for _, publicado in respuestas):
            break
    buscar(pac)
    check("D-23. con dos envíos simultáneos (doble clic) se publica uno solo y el otro se rechaza (%s, %d)"
          % (respuestas, filas_disponibles(pac, f2).count()),
          [estado for estado, _ in respuestas] == [200, 200] and sorted(p for _, p in respuestas) == [False, True]
          and filas_disponibles(pac, f2).count() == 1)

    # ---- D-17 y D-18. La Agenda del paciente ---------------------------------------------------------
    print("-- D-17 y D-18. La Agenda del paciente")
    titulos = pac.locator(".content h3").all_inner_texts()
    check("D-17. la búsqueda está antes que «Mis turnos» (%s)" % " / ".join(titulos),
          titulos.index("Buscar profesional") < titulos.index("Mis turnos"))
    tabla = pac.inner_text("#formAgenda\\:tablaTurnos")
    check("D-18. los días se muestran en español",
          any(d in tabla for d in DIAS) and not any(d in tabla for d in DAYS))

    # ---- D-30, D-19 y D-29. El pago -----------------------------------------------------------------
    print("-- D-30, D-19 y D-29. El pago")
    reservar(pac, f1)
    check("D-18. el turno en espera también dice el día en español",
          any(d in pac.inner_text("#formConfirmacion") for d in DIAS))
    vencimiento = pac.locator("#formConfirmacion\\:vencimiento")
    vencimiento.click()
    vencimiento.press_sequentially("0228", delay=60)
    check("D-30. al escribir 0228 el vencimiento queda 02/28 (%s)" % vencimiento.input_value(),
          vencimiento.input_value() == "02/28")

    nuevos = revisar_tarjeta(pac)
    check("D-19. sin ningún dato, un único mensaje que nombra todo lo que falta (%s)" % nuevos,
          "Revisá los datos de la tarjeta: faltan el nombre del titular, el número de la tarjeta y el código de seguridad."
          in nuevos and len([m for m in nuevos if "tarjeta" in m]) == 1)
    pac.goto(B + "agenda.xhtml")
    tarjeta(pac, codigo="")
    nuevos = revisar_tarjeta(pac)
    check("D-19. sin el código, un solo mensaje y sin el «tiene 3 o 4 dígitos» (%s)" % nuevos,
          nuevos.count("Revisá los datos de la tarjeta: falta el código de seguridad.") == 1
          and not any("dígitos" in m for m in nuevos))
    check("D-29. si falta algo no se ofrece pagar", pac.locator(".ui-confirm-dialog:visible").count() == 0)

    pac.goto(B + "agenda.xhtml")
    tarjeta(pac)
    pac.click("#formConfirmacion\\:btnConfirmar")
    dialogo = pac.locator(".ui-confirm-dialog:visible")
    dialogo.wait_for()
    texto = " ".join(dialogo.inner_text().split())
    check("D-29. antes de cobrar, el diálogo muestra el importe, la tarjeta, el día y la profesional (%s)" % texto,
          re.search(r"\$\s?[\d.]+,\d\d", texto) is not None and "terminada en 1111" in texto
          and any("del " + d + " " in texto for d in DIAS) and "Ana Lopez" in texto)
    dialogo.locator("button:has-text('Revisar los datos')").click()
    dialogo.wait_for(state="hidden")
    pac.goto(B + "agenda.xhtml")
    check("D-29. «Revisar los datos» no cobra: el turno sigue en espera", "Turno reservado temporalmente" in pac.content())
    tarjeta(pac)
    confirmado = pagar(pac)
    check("D-29. con «Sí, pagar» se cobra y se confirma (%s)" % confirmado,
          "Turno confirmado" in confirmado and "terminada en 1111" in confirmado)

    # ---- D-15. Cada paciente una vez en Historia Clínica ---------------------------------------------
    print("-- D-15. Historia Clínica del profesional")
    reservar(pac, f2)
    tarjeta(pac)
    check("D-15. el paciente confirma un segundo turno con la misma profesional", "Turno confirmado" in pagar(pac))
    prof.goto(B + "historia.xhtml")
    opciones = [o for o in prof.locator("#formSeleccion\\:paciente option").all_inner_texts() if nombre in o]
    check("D-15. con dos turnos, el paciente aparece una sola vez en el selector (%s)" % opciones, len(opciones) == 1)
    prof.select_option("#formSeleccion\\:paciente", label=opciones[0])
    with prof.expect_navigation():
        prof.click("#formSeleccion button:has-text('Ver historia')")
    check("D-15. y su historia se abre", nombre in prof.inner_text("#formHistoria"))

    # ---- D-32. Auditoría sin jerga -------------------------------------------------------------------
    print("-- D-32. Auditoría de la historia clínica")
    pac.goto(B + "historia.xhtml")
    contenido = pac.inner_text(".content")
    check("D-32. el paciente ve quién vio su historia, explicado sin jerga",
          "Quién vio tu historia clínica" in contenido and "Ana Lopez" in contenido
          and "interceptor" not in contenido and "Ley" not in contenido)


    # ---- D-16. Una notificación por vez -------------------------------------------------------------
    print("-- D-16. Notificaciones")
    resumen = lambda: int(re.search(r"(\d+) sin leer", pac.inner_text("#formNotificaciones")).group(1))
    badge = lambda: int((pac.locator(".sidebar .count-badge").all_inner_texts() or ["0"])[0])
    # Las notificaciones llegan de forma asincrónica (la autorización de la obra social de los turnos de recién
    # aparece unos segundos después): antes de marcar se espera a que el conteo no cambie, si no una que llega en el
    # medio compensa la marcada y el contador parece no bajar.
    anterior, fin = None, time.time() + 30
    while True:
        pac.goto(B + "notificaciones.xhtml")
        if resumen() == anterior or time.time() > fin:
            break
        anterior = resumen()
        pac.wait_for_timeout(3000)
    antes, badge_antes = resumen(), badge()
    primera = pac.locator(".notif-unread").first
    asunto = primera.locator("strong").inner_text()
    with pac.expect_response(lambda r: "notificaciones.xhtml" in r.url and r.request.method == "POST"):
        primera.locator("a:has-text('Marcar como leída')").click()
    pac.wait_for_timeout(500)
    despues, badge_despues = resumen(), badge()
    check("D-16. al marcar una («%s») quedan %d sin leer de %d" % (asunto, despues, antes), despues == antes - 1)
    check("D-16. y el contador del menú baja igual (%d -> %d)" % (badge_antes, badge_despues), badge_despues == badge_antes - 1)

    # ---- D-14, D-31, D-25, D-26 y el micrófono ------------------------------------------------------
    print("-- D-14, D-31, D-25, D-26 y micrófono. Telemedicina")
    f4 = publicar_libre(prof, "Telemedicina")
    reservar(pac, f4)
    tarjeta(pac)
    check("D-14. el paciente confirma una teleconsulta (%s)" % f4, "Turno confirmado" in pagar(pac))
    prof.goto(B + "telemedicina.xhtml")
    fila_prof = lambda: prof.locator("#formTelemedicina\\:tablaTeleconsultas tr", has_text=nombre)
    with prof.expect_navigation():
        fila_prof().locator("button:has-text('Iniciar videoconsulta')").click()
    check("D-24. al abrir la sala el mensaje no habla del proveedor ni de REST (%s)" % " ".join(mensajes(prof)),
          any("Abriste la sala" in m for m in mensajes(prof)))
    fin = time.time() + 30
    while True:
        pac.goto(B + "notificaciones.xhtml")
        if "Tu profesional ya está en la sala" in pac.content() or time.time() > fin:
            break
        time.sleep(2)
    check("D-31. al paciente le llega el aviso «Tu profesional ya está en la sala»",
          "Tu profesional ya está en la sala" in pac.content())
    pac.goto(B + "telemedicina.xhtml")
    fila_pac = pac.locator("#formTelemedicina\\:tablaTeleconsultas tr", has_text="Ana Lopez").filter(has_text="Abierta")
    enlace = fila_pac.locator("a:has-text('Unirme a la consulta')").first.get_attribute("href")
    id_sala = re.search(r"id=([^&]+)", enlace).group(1)
    check("D-26. Telemedicina no muestra el id interno de la sala (%s)" % id_sala, id_sala not in pac.inner_text(".content"))
    check("D-24. ni la explicación técnica del proveedor",
          "REST" not in pac.inner_text(".content") and "servicio externo" not in pac.inner_text(".content"))

    pac.context.grant_permissions(["camera", "microphone"], origin="http://localhost:8080")
    sala = pac.context.new_page()
    sala.goto(enlace)
    sala.wait_for_selector("#estado:has-text('En curso')")
    check("D-25. la sala no tiene la nota técnica del proveedor",
          "MediConecta la creó por REST" not in sala.content() and "proveedor externo" not in sala.inner_text("body"))
    microfono = sala.locator("#microfono")
    microfono.click()
    sala.wait_for_selector("#microfono:has-text('Silenciar micrófono')")
    check("Micrófono. se activa y el indicador lo muestra",
          sala.inner_text("#indicadorMicrofono").startswith("Micrófono activado"))
    microfono.click()
    sala.wait_for_selector("#microfono:has-text('Activar micrófono')")
    check("Micrófono. «Silenciar micrófono» lo silencia sin cerrar la sala",
          sala.inner_text("#indicadorMicrofono").startswith("Micrófono silenciado")
          and sala.evaluate("() => audio !== null && audio.getAudioTracks()[0].enabled === false"))

    prof.goto(B + "telemedicina.xhtml")
    fila_prof().locator("button:has-text('Finalizar consulta')").click()
    with prof.expect_navigation():
        prof.locator(".ui-confirm-dialog:visible button:has-text('Sí, finalizar')").click()
    check("D-14. la profesional finaliza la consulta (%s)" % " ".join(mensajes(prof)),
          any("Finalizaste la videoconsulta con " + nombre in m for m in mensajes(prof)))
    sala.wait_for_selector("#estado:has-text('Consulta finalizada')", timeout=15000)
    check("D-14. la sala se cierra sola para el paciente y apaga el micrófono",
          sala.is_visible("#finalizada") and sala.is_disabled("#microfono"))
    check("D-14. la teleconsulta sale de la lista de la profesional", fila_prof().count() == 0)
    pac.goto(B + "telemedicina.xhtml")
    check("D-14. y de la del paciente",
          pac.locator("#formTelemedicina\\:tablaTeleconsultas tr", has_text=f4.split(" ")[0]).count() == 0)
    pac.goto(B + "monitor.xhtml")
    check("D-31. el aviso de la sala figura en el Monitor de eventos del paciente",
          "Sala de videoconsulta abierta" in pac.inner_text(".content"))
    sala.close()

    # ---- D-36. Dos pacientes, el mismo horario, al mismo tiempo ----------------------------------------
    print("-- D-36. Dos pacientes reservan el mismo horario a la vez")
    otro_email = "defectos.otro.%d@mediconecta.test" % sufijo
    registro = pagina(br)
    registro.goto(B + "registro.xhtml")
    for campo, valor in (("nombre", "Paciente Defectos Otro %d" % sufijo), ("email", otro_email), ("password", "demo1234"),
                         ("repeticion", "demo1234")):
        registro.fill("#formRegistro\\:" + campo, valor)
    with registro.expect_navigation():
        registro.click("#formRegistro button:has-text('Registrarme')")
    registro.context.close()
    otro = login(br, otro_email)
    f5 = publicar_libre(prof)
    buscar(pac)
    buscar(otro)
    # El primero envía su reserva por fetch (sin esperar) y el segundo hace clic enseguida: los dos pedidos llegan juntos.
    filas_disponibles(pac, f5).locator("button:has-text('Reservar')").first.evaluate("""b => {
        const datos = new FormData(b.form);
        datos.append(b.name, b.name);
        window.__reserva = null;
        fetch(b.form.action, {method: 'POST', body: new URLSearchParams(datos)})
            .then(async r => { window.__reserva = [r.status, await r.text()]; });
    }""")
    with otro.expect_navigation():
        filas_disponibles(otro, f5).locator("button:has-text('Reservar')").first.click()
    pac.wait_for_function("() => window.__reserva !== null", timeout=30000)
    estado_primero, html_primero = pac.evaluate("() => window.__reserva")
    gano_primero = "Turno reservado temporalmente" in html_primero
    gano_otro = "Turno reservado temporalmente" in otro.content()
    check("D-36. los dos pedidos responden bien, sin error del servidor (%d y la página del segundo)" % estado_primero,
          estado_primero == 200 and "error" not in otro.url)
    check("D-36. uno solo se queda con el turno (primero: %s, segundo: %s)" % (gano_primero, gano_otro),
          gano_primero != gano_otro)
    perdedor = " ".join(mensajes(otro)) if gano_primero else html_primero
    check("D-36. al otro se le avisa que el turno ya no está disponible",
          "disponible" in perdedor or "otro paciente" in perdedor or "en espera" in perdedor)
    pac.goto(B + "agenda.xhtml")
    if "Turno reservado temporalmente" in pac.content():
        with pac.expect_navigation():
            pac.click("#formConfirmacion button:has-text('Liberar turno')")
    otro.goto(B + "agenda.xhtml")
    if "Turno reservado temporalmente" in otro.content():
        with otro.expect_navigation():
            otro.click("#formConfirmacion button:has-text('Liberar turno')")
    otro.context.close()

    # ---- D-36 (paso 1). Otra transacción modifica el turno mientras el paciente cancela ----------------------
    # Como la autorización asincrónica de la obra social: otra transacción sube la versión del turno y lo retiene 3 s.
    # La cancelación tiene que esperarla y cancelar sobre la versión nueva (con Hibernate 7, em.find con
    # PESSIMISTIC_WRITE bloqueaba la fila después de leer el turno y el commit terminaba en un error 500).
    datasource = datasource_de_wildfly() if WILDFLY_HOME else None
    if datasource is None:
        print("-- D-36 (paso 1): se omite; necesita WILDFLY_HOME con el datasource de MediConecta configurado")
    else:
        print("-- D-36 (paso 1). El paciente cancela mientras otra transacción modifica el mismo turno")
        f6 = publicar_libre(prof)
        reservar(pac, f6)
        tarjeta(pac)
        check("D-36. el paciente confirma un tercer turno", "Turno confirmado" in pagar(pac))
        token_paciente = api("auth/login", "POST", {"email": email, "password": "demo1234"})[1]["token"]
        dia, hora = f6.split(" ")
        iso = "%s-%s-%sT%s" % (dia[6:], dia[3:5], dia[:2], hora)
        turno = next(t["id"] for t in api("turnos", token=token_paciente)[1]
                     if t["fechaHora"].startswith(iso) and t["estado"] == "CONFIRMADO")
        time.sleep(8)  # que termine la autorización de la obra social: compiten sólo la otra transacción y la cancelación
        driver, credenciales = datasource
        otra = subprocess.Popen(["java", "-cp", driver, BLOQUEO, str(turno), "3000"], env={**os.environ, **credenciales},
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        retenido = otra.stdout.readline().strip() == "bloqueado 1"
        inicio = time.time()
        estado, cuerpo = api("turnos/%d/cancelacion" % turno, "POST", token=token_paciente)
        demora = time.time() - inicio
        otra.wait(30)
        check("D-36. la otra transacción modificó el turno y lo retuvo mientras el paciente cancelaba", retenido)
        check("D-36. la cancelación la espera (%.1f s) y cancela sobre la versión nueva, sin error 500 (%d)" % (demora, estado),
              estado == 200 and isinstance(cuerpo, dict) and cuerpo.get("estado") == "CANCELADO" and demora >= 2)

    # ---- D-38. La pantalla Pagos del administrativo con cientos de filas ---------------------------------
    # Antes cada fila leía su turno con una consulta aparte (N+1): con 355 cobros y 165 reclamos tardaba 30 s.
    print("-- D-38. La pantalla Pagos del administrativo")
    adm = pagina(br)
    adm.goto(B + "login.xhtml")
    adm.fill("#formLogin\\:email", ADMINISTRATIVO)
    adm.fill("#formLogin\\:password", "demo1234")
    inicio = time.time()
    with adm.expect_navigation(timeout=120000):
        adm.click("#formLogin button:has-text('Ingresar')")
    demora = time.time() - inicio
    cobros = adm.locator("[id$='tablaPagos'] tbody tr").count()
    check("D-38. el administrativo entra a Pagos en pocos segundos (%.1f s, %d cobros)" % (demora, cobros),
          "pagos.xhtml" in adm.url and cobros > 0 and demora < 10)
    adm.context.close()

    # ---- D-24, D-27 y D-28. Pantallas ---------------------------------------------------------------
    print("-- D-24, D-27 y D-28. Pantallas")
    jerga = re.compile(r"\b(REST|SOAP|WSDL|JMS|MDB|DLQ)\b|t[oó]pico|asincr|interceptor|cach[eé]|\bhold\b|pasarela")
    for pantalla in ("agenda.xhtml", "historia.xhtml", "telemedicina.xhtml", "notificaciones.xhtml", "pagos.xhtml"):
        pac.goto(B + pantalla)
        encontrada = jerga.search(pac.inner_text(".content"))
        check("D-24. %s sin jerga técnica%s" % (pantalla, "" if not encontrada else " (dice «%s»)" % encontrada.group(0)),
              encontrada is None)
    ancho = pagina(br)
    ancho.set_viewport_size({"width": 1920, "height": 1080})
    ancho.goto(B + "login.xhtml")
    ancho.fill("#formLogin\\:email", email)
    ancho.fill("#formLogin\\:password", "demo1234")
    with ancho.expect_navigation():
        ancho.click("#formLogin button:has-text('Ingresar')")
    medida = ancho.evaluate("() => document.querySelector('.content').getBoundingClientRect().width")
    check("D-27. en un monitor de 1920 px el contenido aprovecha el ancho (%d px)" % medida, medida >= 1550)
    check("D-28. «Mi perfil» no se repite en el menú y se abre desde la tarjeta del usuario",
          "Mi perfil" not in ancho.inner_text(".sidebar .nav")
          and ancho.locator(".sidebar a.user-chip[title='Mi perfil']").count() == 1)
    ancho.context.close()

    prof.context.close()
    pac.context.close()
    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
