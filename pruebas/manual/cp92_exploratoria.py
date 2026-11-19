"""CP-92, prueba exploratoria guiada (asistida por IA, 19/11): un cargo de prueba (charter) por tema, con una captura
por paso en resultados-manual/cp92/. No es una suite: no decide si algo está bien, imprime lo OBSERVADO para que una
persona lo revise y lo registre (así se encontraron D-45 y D-46). Uso: python pruebas/manual/cp92_exploratoria.py"""
import os, random, re, sys
from datetime import date, timedelta
from playwright.sync_api import sync_playwright

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass
B = "https://localhost:8443/mediconecta/"
SALIDA = os.path.join(os.getcwd(), "resultados-manual", "cp92")
os.makedirs(SALIDA, exist_ok=True)
paso = [0]


def foto(pg, nombre):
    paso[0] += 1
    pg.screenshot(path=os.path.join(SALIDA, "%02d-%s.png" % (paso[0], nombre)), full_page=False)


def obs(charter, texto):
    print("[%s] %s" % (charter, texto))


def pagina(br):
    return br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()


def login(br, email):
    pg = pagina(br)
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", "demo1234")
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg


def mensajes(pg):
    return pg.locator(".ui-growl-message").all_inner_texts()


def registrar(br, nombre, email, afiliado=""):
    pg = pagina(br)
    pg.goto(B + "registro.xhtml")
    for campo, valor in (("nombre", nombre), ("email", email), ("password", "demo1234"), ("repeticion", "demo1234"),
                         ("obraSocial", ""), ("nroAfiliado", afiliado)):
        pg.fill("#formRegistro\\:" + campo, valor)
    with pg.expect_navigation():
        pg.click("#formRegistro button:has-text('Registrarme')")
    return pg


def publicar(prof):
    for _ in range(15):
        dia = (date.today() + timedelta(days=random.randint(47, 53))).strftime("%d/%m/%Y")
        fecha = "%s %02d:%02d" % (dia, random.randint(7, 19), random.randint(0, 11) * 5)
        prof.goto(B + "agenda.xhtml")
        prof.fill("#formPublicar\\:fecha_input", fecha)
        prof.keyboard.press("Escape")
        with prof.expect_navigation():
            prof.locator("#formPublicar button:has-text('Publicar')").evaluate("b => b.click()")
        if any("Disponibilidad publicada" in m for m in mensajes(prof)):
            return fecha
    raise AssertionError("sin horario libre")


def reservar(pac, fecha):
    pac.goto(B + "agenda.xhtml")
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    d, h = fecha.split(" ")
    with pac.expect_navigation():
        pac.locator("#formAgenda\\:tablaTurnos tr", has_text=d + " · " + h).locator("button:has-text('Reservar')").first.click()


def tarjeta(pac, numero="4111 1111 1111 1111"):
    pac.fill("#formConfirmacion\\:titular", "Exploratoria")
    pac.fill("#formConfirmacion\\:numeroTarjeta", numero)
    pac.fill("#formConfirmacion\\:vencimiento", "")
    pac.fill("#formConfirmacion\\:vencimiento", "12/29")
    pac.fill("#formConfirmacion\\:codigoSeguridad", "123")


def cobros(pac, fecha):
    pac.goto(B + "pagos.xhtml")
    d = fecha.split(" ")[0]
    return pac.locator("tr", has_text=d).count()


with sync_playwright() as p:
    br = p.chromium.launch(channel="msedge", headless=True)
    s = random.randint(10000, 99999)
    email = "explora.%d@mediconecta.test" % s
    nombre = "Paciente Explora %d" % s
    prof = login(br, "ana.lopez@mediconecta.test")

    # ---- Datos inválidos ----------------------------------------------------------------------
    c = "datos inválidos"
    pg = pagina(br)
    pg.goto(B + "registro.xhtml")
    for campo, valor in (("nombre", "X"), ("email", "a@b"), ("password", "123"), ("repeticion", "456"),
                         ("nroAfiliado", "9" * 41)):
        pg.fill("#formRegistro\\:" + campo, valor)
    with pg.expect_navigation():
        pg.click("#formRegistro button:has-text('Registrarme')")
    foto(pg, "registro-invalido")
    obs(c, "registro con email 'a@b', contraseña corta, repetición distinta y afiliado de 41: %s"
        % (mensajes(pg) or pg.locator(".ui-message-error, .ui-messages-error").all_inner_texts()))
    pg.context.close()
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", "32/13/2026 25:00")
    prof.keyboard.press("Escape")
    with prof.expect_navigation():
        prof.locator("#formPublicar button:has-text('Publicar')").evaluate("b => b.click()")
    foto(prof, "publicar-fecha-invalida")
    obs(c, "publicar '32/13/2026 25:00': %s" % (mensajes(prof) or prof.locator(".ui-message").all_inner_texts()))

    registrar(br, nombre, email).context.close()
    pac = login(br, email)
    f1 = publicar(prof)
    reservar(pac, f1)
    tarjeta(pac, "1234")
    pac.click("#formConfirmacion\\:btnConfirmar")
    pac.wait_for_timeout(1500)
    foto(pac, "tarjeta-invalida")
    obs(c, "tarjeta '1234': %s" % mensajes(pac))

    # ---- Doble clic en "Sí, pagar" ------------------------------------------------------------------------
    c = "doble clic"
    tarjeta(pac)
    pac.click("#formConfirmacion\\:btnConfirmar")
    boton = pac.locator(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    boton.wait_for()
    try:
        with pac.expect_navigation(timeout=30000):
            boton.dblclick()
    except Exception as e:
        obs(c, "la navegación después del doble clic: %s" % type(e).__name__)
    foto(pac, "doble-clic-pagar")
    obs(c, "doble clic en 'Sí, pagar': %s" % mensajes(pac))
    obs(c, "filas en Pagos para el %s: %d (esperado 1)" % (f1.split(" ")[0], cobros(pac, f1)))

    # ---- Recarga después de pagar -------------------------------------------------------------------
    c = "recarga"
    f2 = publicar(prof)
    reservar(pac, f2)
    tarjeta(pac)
    pac.click("#formConfirmacion\\:btnConfirmar")
    with pac.expect_navigation():
        pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
    obs(c, "después de pagar la URL es %s" % pac.url)
    pac.reload()
    foto(pac, "recarga-despues-de-pagar")
    obs(c, "recargar (F5) después de pagar: mensajes %s; filas en Pagos para el %s: %d (esperado 1)"
        % (mensajes(pac), f2.split(" ")[0], cobros(pac, f2)))

    # ---- Doble clic en Guardar de la historia clínica + HTML en el diagnóstico ---------------------------
    c = "doble clic / inyección en la ficha"
    prof.goto(B + "historia.xhtml")
    opcion = next(o for o in prof.locator("#formSeleccion\\:paciente option").all_inner_texts() if nombre in o)
    prof.select_option("#formSeleccion\\:paciente", label=opcion)
    with prof.expect_navigation():
        prof.click("#formSeleccion button:has-text('Ver historia')")
    prof.fill("#formHistoria\\:descripcion", "<b>negrita</b><img src=x onerror=window.__x=1> control %d" % s)
    try:
        with prof.expect_navigation(timeout=30000):
            prof.locator("#formHistoria button:has-text('Guardar')").dblclick()
    except Exception as e:
        obs(c, "la navegación después del doble clic: %s" % type(e).__name__)
    prof.wait_for_timeout(1500)
    foto(prof, "doble-clic-guardar")
    obs(c, "doble clic en Guardar: '%s'" % re.search(r"\d+ entrada\(s\)", prof.inner_text("#formHistoria")).group(0))
    with prof.expect_response(lambda r: r.request.method == "POST"):
        prof.locator("#formHistoria\\:verFicha").click()
    prof.locator("#dialogoFicha").wait_for(state="visible")
    foto(prof, "ficha-con-html")
    obs(c, "en la ficha el HTML se ve como texto: %s; <b> interpretado: %s; onerror ejecutado: %s"
        % ("<b>negrita</b>" in prof.locator("#dialogoFicha").inner_text(),
           prof.locator("#dialogoFicha b", has_text="negrita").count() > 0, prof.evaluate("window.__x === 1")))
    prof.keyboard.press("Escape")

    # ---- Dos usuarios: la profesional cancela mientras el paciente tiene la Agenda abierta --------------
    c = "dos usuarios"
    pac.goto(B + "agenda.xhtml")
    d2 = f2.split(" ")[0]
    prof.goto(B + "agenda.xhtml")
    fila = prof.locator("#formTurnosProfesional\\:tablaConfirmadosProfesional tr", has_text=nombre).filter(has_text=d2)
    fila.locator("button:has-text('Cancelar')").first.click()
    with prof.expect_navigation():
        prof.click(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')")
    obs(c, "la profesional cancela: %s" % mensajes(prof))
    fila_pac = pac.locator("#formAgenda\\:tablaMisTurnos tr", has_text=d2.split("/")[0] + "/" + d2.split("/")[1])
    if fila_pac.count():
        fila_pac.locator("button:has-text('Cancelar')").first.click()
        with pac.expect_navigation():
            pac.click(".ui-confirm-dialog:visible button:has-text('Sí, cancelar el turno')")
        foto(pac, "dos-usuarios-cancelar-otra-vez")
        obs(c, "el paciente, con la página vieja, cancela el mismo turno: %s" % mensajes(pac))
    else:
        obs(c, "la fila del paciente no estaba en la página")

    # ---- Volver atrás después de cerrar sesión ------------------------------------------------------
    c = "volver atrás"
    pac.goto(B + "historia.xhtml")
    with pac.expect_navigation():
        pac.click("button:has-text('Cerrar sesión')")
    pac.go_back()
    pac.wait_for_timeout(800)
    foto(pac, "atras-despues-de-salir")
    obs(c, "atrás después de cerrar sesión: URL %s; se ven datos de la historia: %s"
        % (pac.url, nombre in pac.inner_text("body") and "login" not in pac.url))

    # ---- Sesión vencida -----------------------------------------------------------------------------
    c = "sesión vencida"
    pac = login(br, email)
    pac.goto(B + "agenda.xhtml")
    pac.context.clear_cookies()
    sel = pac.locator("#formAgenda\\:profesional")
    sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
    with pac.expect_navigation():
        pac.click("button:has-text('Ver turnos disponibles')")
    foto(pac, "sesion-vencida")
    obs(c, "acción con la sesión vencida: URL %s; título '%s'; error 500 o traza: %s"
        % (pac.url, pac.title(), any(t in pac.content() for t in ("Exception", "500", "ViewExpired"))))
    br.close()
print("capturas en", SALIDA)
