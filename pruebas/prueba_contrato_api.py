"""Pruebas de contrato de la API REST contra su OpenAPI (docs/api/openapi.json).

Cada respuesta documentada se provoca contra la aplicación desplegada y se compara con el contrato: el código, el
tipo de contenido (application/json o application/problem+json), las cabeceras (ETag, Retry-After,
WWW-Authenticate, Idempotent-Replayed) y el cuerpo contra su esquema, sin campos de más. Al final se verifica que
se haya ejercitado cada respuesta del contrato: si se documenta una nueva sin probarla, la prueba lo marca.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war y externos.war desplegados, los
usuarios de demo, Google Chrome (o Edge con NAVEGADOR=msedge) y  pip install playwright  (sólo para preparar dos
turnos confirmados; el resto es la API). Sólo usa la biblioteca estándar para validar.
Uso:  python pruebas/prueba_contrato_api.py
Deja en la base un paciente de prueba por corrida (contrato.NNNNN@mediconecta.test) con dos turnos cancelados con la
Dra. Ana Lopez.
"""
import json, os, random, re, ssl, sys, urllib.error, urllib.request, uuid
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
PACIENTE_DEMO, PROFESIONAL, ADMINISTRATIVO = ("juan.perez@mediconecta.test", "ana.lopez@mediconecta.test",
                                             "carla.admin@mediconecta.test")
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
CONTRATO = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "docs", "api", "openapi.json"),
                          encoding="utf-8"))
# Respuestas del contrato que esta prueba no provoca, y dónde se prueban.
NO_PROVOCADAS = {("/auth/login", "post", "403"): "contraseña temporal: la prueba prueba_entrega4.py (escenario M)"}
ok = True
ejercitadas = set()


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


# ---- Validación contra el contrato (el subconjunto de JSON Schema que usa el OpenAPI) --------------------
def resolver(nodo):
    while isinstance(nodo, dict) and "$ref" in nodo:
        objetivo = CONTRATO
        for parte in nodo["$ref"].lstrip("#/").split("/"):
            objetivo = objetivo[parte]
        nodo = objetivo
    return nodo


def validar(valor, esquema, donde="cuerpo"):
    esquema = resolver(esquema)
    if valor is None:
        return [] if esquema.get("nullable") else ["%s: es null y el contrato no lo admite" % donde]
    tipo = esquema.get("type")
    tipos = {"string": str, "integer": int, "number": (int, float), "boolean": bool, "array": list, "object": dict}
    if tipo and (not isinstance(valor, tipos[tipo]) or (tipo in ("integer", "number") and isinstance(valor, bool))):
        return ["%s: se esperaba %s y llegó %r" % (donde, tipo, valor)]
    errores = []
    if "enum" in esquema and valor not in esquema["enum"]:
        errores.append("%s: %r no está entre %s" % (donde, valor, [v for v in esquema["enum"] if v is not None]))
    if "pattern" in esquema and not re.search(esquema["pattern"], valor):
        errores.append("%s: %r no cumple el formato %s" % (donde, valor, esquema["pattern"]))
    if "minimum" in esquema and valor < esquema["minimum"]:
        errores.append("%s: %r es menor que %s" % (donde, valor, esquema["minimum"]))
    if "maximum" in esquema and valor > esquema["maximum"]:
        errores.append("%s: %r es mayor que %s" % (donde, valor, esquema["maximum"]))
    if tipo == "array":
        for i, elemento in enumerate(valor):
            errores += validar(elemento, esquema["items"], "%s[%d]" % (donde, i))
    if tipo == "object":
        propiedades = esquema.get("properties", {})
        errores += ["%s: falta «%s»" % (donde, campo) for campo in esquema.get("required", []) if campo not in valor]
        if esquema.get("additionalProperties") is False:
            errores += ["%s: «%s» no está en el contrato" % (donde, campo) for campo in valor if campo not in propiedades]
        for campo, subesquema in propiedades.items():
            if campo in valor:
                errores += validar(valor[campo], subesquema, "%s.%s" % (donde, campo))
    return errores


def cumple(nombre, plantilla, metodo, respuesta):
    """La respuesta coincide con el contrato: código documentado, tipo de contenido, cabeceras y cuerpo."""
    estado, cabeceras, texto = respuesta
    operacion = CONTRATO["paths"][plantilla][metodo]
    documentada = operacion["responses"].get(str(estado))
    errores = []
    if documentada is None:
        errores.append("el código %d no está documentado para %s %s" % (estado, metodo.upper(), plantilla))
    else:
        ejercitadas.add((plantilla, metodo, str(estado)))
        documentada = resolver(documentada)
        tipo = (cabeceras.get("Content-Type") or "").split(";")[0].strip()
        contenido = documentada.get("content", {})
        if contenido:
            if tipo not in contenido:
                errores.append("tipo de contenido %r, el contrato dice %s" % (tipo, list(contenido)))
            else:
                try:
                    errores += validar(json.loads(texto), contenido[tipo]["schema"])
                except ValueError:
                    errores.append("el cuerpo no es JSON: %r" % texto[:80])
        elif texto.strip():
            errores.append("el contrato no tiene cuerpo y llegó %r" % texto[:80])
        for cabecera, definicion in documentada.get("headers", {}).items():
            if cabecera == "Idempotent-Replayed":
                continue  # sólo en la repetición: se verifica aparte
            valor = cabeceras.get(cabecera)
            if valor is None:
                errores.append("falta la cabecera %s" % cabecera)
            else:
                esquema = resolver(definicion)["schema"]
                errores += validar(int(valor) if esquema.get("type") == "integer" and valor.isdigit() else valor,
                                   esquema, cabecera)
    check("%s → %d según el contrato%s" % (nombre, estado, (": " + "; ".join(errores[:3])) if errores else ""), not errores)
    return respuesta


# ---- HTTP -----------------------------------------------------------------------------------------------------
def pedir(metodo, ruta, cuerpo=None, token=None, cabeceras=None, tipo="application/json"):
    datos = None if cuerpo is None else (cuerpo if isinstance(cuerpo, bytes) else json.dumps(cuerpo).encode())
    pedido = urllib.request.Request(B + "api/v1" + ruta, method=metodo, data=datos if metodo != "POST" or datos else b"")
    pedido.add_header("Content-Type", tipo)
    pedido.add_header("Accept", (cabeceras or {}).pop("Accept", "application/json"))
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    for nombre, valor in (cabeceras or {}).items():
        pedido.add_header(nombre, valor)
    try:
        with urllib.request.urlopen(pedido, timeout=20) as respuesta:
            return respuesta.status, dict(respuesta.headers), respuesta.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read().decode()


def token(email):
    return json.loads(pedir("POST", "/auth/login", {"email": email, "password": "demo1234"})[2])["token"]


def dia_desde_hoy(dias):
    return (date.today() + timedelta(days=dias)).strftime("%d/%m/%Y")


def consola(**modos):
    pedido = urllib.request.Request(EXT + "api/consola/estado", method="PUT", data=json.dumps(modos).encode(),
                                    headers={"Content-Type": "application/json"})
    urllib.request.urlopen(pedido, timeout=10)


def dos_turnos_confirmados(email):
    """Prepara, por la web, dos turnos confirmados del paciente con la Dra. Ana Lopez (la API no reserva)."""
    fechas = []
    with sync_playwright() as p:
        br = p.chromium.launch(channel=NAVEGADOR, headless=True)
        nueva = lambda: br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()
        registro = nueva()
        registro.goto(B + "registro.xhtml")
        for campo, valor in (("nombre", "Paciente Contrato"), ("email", email), ("password", "demo1234"),
                             ("repeticion", "demo1234")):
            registro.fill("#formRegistro\\:" + campo, valor)
        with registro.expect_navigation():
            registro.click("#formRegistro button:has-text('Registrarme')")

        def ingresar(correo):
            pg = nueva()
            pg.goto(B + "login.xhtml")
            pg.fill("#formLogin\\:email", correo)
            pg.fill("#formLogin\\:password", "demo1234")
            with pg.expect_navigation():
                pg.click("#formLogin button:has-text('Ingresar')")
            return pg

        prof, pac = ingresar(PROFESIONAL), ingresar(email)
        while len(fechas) < 2:
            fecha = "%s %02d:%02d" % (dia_desde_hoy(random.randint(56, 62)), random.randint(7, 19), random.randint(0, 11) * 5)
            prof.goto(B + "agenda.xhtml")
            prof.fill("#formPublicar\\:fecha_input", fecha)
            prof.keyboard.press("Escape")
            with prof.expect_navigation():
                prof.locator("#formPublicar button:has-text('Publicar')").evaluate("boton => boton.click()")
            if "Disponibilidad publicada" not in " ".join(prof.locator(".ui-growl-message").all_inner_texts()):
                continue
            dia, hora = fecha.split(" ")
            pac.goto(B + "agenda.xhtml")
            sel = pac.locator("#formAgenda\\:profesional")
            sel.select_option(sel.locator("option", has_text="Ana Lopez").first.get_attribute("value"))
            with pac.expect_navigation():
                pac.click("button:has-text('Ver turnos disponibles')")
            with pac.expect_navigation():
                pac.locator("#formAgenda\\:tablaTurnos tr", has_text=dia + " · " + hora).locator("button:has-text('Reservar')").first.click()
            pac.fill("#formConfirmacion\\:titular", "Paciente Contrato")
            pac.fill("#formConfirmacion\\:numeroTarjeta", "4111 1111 1111 1111")
            pac.fill("#formConfirmacion\\:vencimiento", "")
            pac.fill("#formConfirmacion\\:vencimiento", "12/29")
            pac.fill("#formConfirmacion\\:codigoSeguridad", "123")
            pac.click("#formConfirmacion\\:btnConfirmar")
            with pac.expect_navigation():
                pac.click(".ui-confirm-dialog:visible button:has-text('Sí, pagar')")
            if "Turno confirmado" in pac.content():
                fechas.append(fecha)
        br.close()
    return fechas


consola(pasarela="EN_LINEA", obraSocial="EN_LINEA", autorizaciones="APROBAR")
sufijo = random.randint(10000, 99999)

# ---- Autenticación --------------------------------------------------------------------------------------------
print("-- POST /auth/login")
cumple("login del paciente de demo", "/auth/login", "post", pedir("POST", "/auth/login", {"email": PACIENTE_DEMO, "password": "demo1234"}))
cumple("login con una cuenta que no existe", "/auth/login", "post",
       pedir("POST", "/auth/login", {"email": "contrato.no.existe.%d@mediconecta.test" % sufijo, "password": "x"}))
cumple("login sin cuerpo", "/auth/login", "post", pedir("POST", "/auth/login"))
cumple("login sin contraseña", "/auth/login", "post", pedir("POST", "/auth/login", {"email": PACIENTE_DEMO}))
cumple("login con un cuerpo que no es JSON", "/auth/login", "post",
       pedir("POST", "/auth/login", b"email=x&password=y", tipo="text/plain"))
bloqueado = "contrato.bloqueo.%d@mediconecta.test" % sufijo
for _ in range(5):
    pedir("POST", "/auth/login", {"email": bloqueado, "password": "incorrecta"})
cumple("login después de 5 fallos seguidos", "/auth/login", "post", pedir("POST", "/auth/login", {"email": bloqueado, "password": "x"}))

# ---- Profesionales ---------------------------------------------------------------------------------------------
print("-- GET /profesionales")
_, _, cuerpo = cumple("listado de profesionales", "/profesionales", "get", pedir("GET", "/profesionales"))
ana = next(p["id"] for p in json.loads(cuerpo) if "Ana Lopez" in p["nombre"])
cumple("listado de profesionales pidiendo XML", "/profesionales", "get", pedir("GET", "/profesionales", cabeceras={"Accept": "application/xml"}))
cumple("disponibilidad de la Dra. Ana Lopez", "/profesionales/{id}/disponibilidad", "get", pedir("GET", "/profesionales/%d/disponibilidad" % ana))
cumple("disponibilidad con un id que no es un número", "/profesionales/{id}/disponibilidad", "get", pedir("GET", "/profesionales/abc/disponibilidad"))

# ---- Turnos ----------------------------------------------------------------------------------------------------
print("-- GET /turnos y /turnos/{id}")
email = "contrato.%d@mediconecta.test" % sufijo
fechas = dos_turnos_confirmados(email)
tk, tk_adm, tk_otro = token(email), token(ADMINISTRATIVO), token(PACIENTE_DEMO)
_, _, cuerpo = cumple("turnos del paciente", "/turnos", "get", pedir("GET", "/turnos", token=tk))
ids = [t["id"] for t in json.loads(cuerpo) if t["estado"] == "CONFIRMADO"]
check("el paciente tiene los dos turnos confirmados que se prepararon (%s)" % fechas, len(ids) == 2)
primero, segundo = ids
cumple("turnos sin token", "/turnos", "get", pedir("GET", "/turnos"))
cumple("turnos con el token del administrativo", "/turnos", "get", pedir("GET", "/turnos", token=tk_adm))
cumple("DELETE sobre la colección de turnos", "/turnos", "delete", pedir("DELETE", "/turnos", token=tk))
estado, cabeceras, _ = cumple("un turno del paciente", "/turnos/{id}", "get", pedir("GET", "/turnos/%d" % primero, token=tk))
etag = cabeceras.get("ETag")
cumple("el mismo turno con If-None-Match", "/turnos/{id}", "get",
       pedir("GET", "/turnos/%d" % primero, token=tk, cabeceras={"If-None-Match": etag or ""}))
cumple("un turno que no existe", "/turnos/{id}", "get", pedir("GET", "/turnos/999999999", token=tk))
cumple("un turno ajeno (como si no existiera)", "/turnos/{id}", "get", pedir("GET", "/turnos/%d" % primero, token=tk_otro))
cumple("un turno sin token", "/turnos/{id}", "get", pedir("GET", "/turnos/%d" % primero))
cumple("un turno con el token del administrativo", "/turnos/{id}", "get", pedir("GET", "/turnos/%d" % primero, token=tk_adm))
cumple("un turno pidiendo XML", "/turnos/{id}", "get",
       pedir("GET", "/turnos/%d" % primero, token=tk, cabeceras={"Accept": "application/xml"}))

print("-- POST /turnos/{id}/cancelacion")
ruta = "/turnos/{id}/cancelacion"
clave = "contrato-%s" % uuid.uuid4()
cumple("cancelar sin token", ruta, "post", pedir("POST", "/turnos/%d/cancelacion" % primero))
cumple("cancelar un turno ajeno", ruta, "post", pedir("POST", "/turnos/%d/cancelacion" % primero, token=tk_otro))
cumple("cancelar con una Idempotency-Key mal formada", ruta, "post",
       pedir("POST", "/turnos/%d/cancelacion" % primero, token=tk, cabeceras={"Idempotency-Key": "con espacios no vale"}))
cumple("cancelar con un If-Match viejo", ruta, "post",
       pedir("POST", "/turnos/%d/cancelacion" % primero, token=tk, cabeceras={"If-Match": '"999"'}))
estado, cabeceras, original = cumple("cancelar con If-Match vigente e Idempotency-Key", ruta, "post",
                                     pedir("POST", "/turnos/%d/cancelacion" % primero, token=tk,
                                           cabeceras={"If-Match": etag or "", "Idempotency-Key": clave}))
check("la cancelación no repetida no lleva Idempotent-Replayed", "Idempotent-Replayed" not in cabeceras)
estado, cabeceras, repetida = cumple("repetir la misma cancelación con la misma clave", ruta, "post",
                                     pedir("POST", "/turnos/%d/cancelacion" % primero, token=tk, cabeceras={"Idempotency-Key": clave}))
check("la repetición trae Idempotent-Replayed: true y el mismo cuerpo",
      cabeceras.get("Idempotent-Replayed") == "true" and json.loads(repetida) == json.loads(original))
cumple("la misma clave en otro turno", ruta, "post",
       pedir("POST", "/turnos/%d/cancelacion" % segundo, token=tk, cabeceras={"Idempotency-Key": clave}))
cumple("cancelar otra vez el turno ya cancelado (patrón State)", ruta, "post",
       pedir("POST", "/turnos/%d/cancelacion" % primero, token=tk))
pedir("POST", "/turnos/%d/cancelacion" % segundo, token=tk)  # no deja turnos confirmados de prueba

# ---- Cerrar la sesión (ADR-0019) -------------------------------------------------------------------------
print("-- POST /auth/logout")
tk_salida = token(email)
cumple("cerrar la sesión de la API", "/auth/logout", "post", pedir("POST", "/auth/logout", token=tk_salida))
estado, _, cuerpo = cumple("usar el token después de cerrar la sesión", "/turnos", "get", pedir("GET", "/turnos", token=tk_salida))
check("el token revocado se rechaza como inválido aunque no venció (%s)" % json.loads(cuerpo).get("error"),
      estado == 401 and json.loads(cuerpo).get("error") == "token_invalido")
cumple("cerrar la sesión otra vez con el mismo token", "/auth/logout", "post", pedir("POST", "/auth/logout", token=tk_salida))
cumple("cerrar la sesión sin token", "/auth/logout", "post", pedir("POST", "/auth/logout"))
check("un login nuevo del mismo paciente da un token que sí vale", pedir("GET", "/turnos", token=token(email))[0] == 200)

# ---- Cobertura del contrato ----------------------------------------------------------------------------------------
documentadas = {(plantilla, metodo, codigo) for plantilla, operaciones in CONTRATO["paths"].items()
                for metodo, operacion in operaciones.items() for codigo in operacion["responses"]}
faltan = sorted(documentadas - ejercitadas - set(NO_PROVOCADAS))
check("se ejercitaron %d de las %d respuestas del contrato%s" % (len(ejercitadas), len(documentadas),
      "; faltan: %s" % faltan if faltan else " (la que falta se prueba en otra suite: %s)" % "; ".join(NO_PROVOCADAS.values())),
      not faltan)

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
