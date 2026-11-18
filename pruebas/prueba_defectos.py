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

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados, Google
Chrome (o Edge con NAVEGADOR=msedge) y  pip install playwright
Uso:  python pruebas/prueba_defectos.py
Deja en la base un paciente de prueba por corrida (defectos.NNNNN@mediconecta.test, OSDE) con dos turnos confirmados
y los horarios publicados en diciembre con la Dra. Ana Lopez. Tarda unos 2 minutos: en D-20 espera que se cierre el
Circuit Breaker de la obra social.
"""
import json, os, random, re, ssl, sys, time, urllib.request
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
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
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


def fechas_libres(cantidad):
    """Horarios distintos de fines de diciembre, lejos de los días que usan las otras pruebas."""
    elegidas = set()
    while len(elegidas) < cantidad:
        elegidas.add("%02d/12/2026 %02d:%02d" % (random.randint(21, 27), random.randint(7, 19), random.choice(range(5, 60, 10))))
    return sorted(elegidas)


def publicar(prof, fecha):
    prof.goto(B + "agenda.xhtml")
    prof.fill("#formPublicar\\:fecha_input", fecha)
    prof.keyboard.press("Escape")
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
            fetch(b.form.action, {method: 'POST', body: new URLSearchParams(datos)}).then(r => r.status)));
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
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
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

    f1, f2, f3, f4 = fechas_libres(4)
    prof = login(br, PROFESIONAL)
    pac = login(br, email)

    # ---- D-20. Consultar de nuevo a la obra social -------------------------------------------------
    # Va primero: el paciente todavía no tiene ninguna cobertura en la caché, así que con el legado caído queda pendiente.
    print("-- D-20. Consultar de nuevo a la obra social")
    publicar(prof, f3)
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
    check("D-23. la profesional publica un horario (%s)" % f1, "Disponibilidad publicada" in publicar(prof, f1))
    dia, hora = f1.split(" ")
    repetido = publicar(prof, f1)
    check("D-23. si lo publica otra vez, se rechaza con un mensaje claro",
          "Ya tenés publicado un horario el %s a las %s hs." % (dia, hora) in repetido)
    buscar(pac)
    check("D-23. el paciente ve ese horario una sola vez", filas_disponibles(pac, f1).count() == 1)
    estados = publicar_dos_veces_a_la_vez(prof, f2)
    buscar(pac)
    check("D-23. con dos envíos simultáneos (doble clic) tampoco queda repetido (%s, %d)"
          % (estados, filas_disponibles(pac, f2).count()), estados == [200, 200] and filas_disponibles(pac, f2).count() == 1)

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

    prof.context.close()
    pac.context.close()
    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
