"""Crea los usuarios de demo en una base vacía, a través de la propia aplicación (sin SQL).

Los que usan las pruebas de punta a punta y la demo (contraseña demo1234 en los tres):
  - Dra. Ana Lopez (ana.lopez@mediconecta.test), profesional, MN 12345, Cardiología.
  - Juan Perez (juan.perez@mediconecta.test), paciente con OSDE 210.
  - Carla Admin (carla.admin@mediconecta.test), administrativa.

Cómo: el paciente se registra solo, como cualquier paciente. A la profesional y a la administrativa las da de alta
el administrador por defecto (admin@mediconecta.test, que la aplicación crea al desplegar con la contraseña inicial
del servidor); cada una entra con su contraseña temporal y la cambia por demo1234 en Mi perfil, como en el uso real.

Requisitos: MediConecta desplegada sobre una base nueva, Google Chrome (o Edge con NAVEGADOR=msedge) y
pip install playwright. Variables de entorno:
  MEDICONECTA_ADMIN_PASSWORD  la contraseña inicial del administrador (la misma que se le dio al servidor).
  MEDICONECTA_ADMIN_NUEVA     la que queda después del primer ingreso (opcional: si falta, se genera al azar).
Uso:  python pruebas/sembrar_usuarios_demo.py
Si un usuario ya existe (entra con demo1234), lo deja como está: se puede correr más de una vez.
"""
import json, os, secrets, ssl, sys, urllib.error, urllib.request
from playwright.sync_api import sync_playwright

# MediConecta corre por HTTPS con el certificado autofirmado que WildFly genera para localhost: se acepta sin validar.
urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))
try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = os.environ.get("MEDICONECTA_URL", "https://localhost:8443/mediconecta/")
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
CLAVE_DEMO = "demo1234"
ADMIN = "admin@mediconecta.test"
PACIENTE = {"nombre": "Juan Perez", "email": "juan.perez@mediconecta.test", "obraSocial": "OSDE 210", "nroAfiliado": "210451"}
POR_EL_ADMIN = [
    {"rol": "PROFESIONAL", "nombre": "Dra. Ana Lopez", "email": "ana.lopez@mediconecta.test",
     "matricula": "MN 12345", "especialidad": "Cardiología", "inicio": "agenda.xhtml"},
    {"rol": "ADMINISTRATIVO", "nombre": "Carla Admin", "email": "carla.admin@mediconecta.test", "inicio": "pagos.xhtml"},
]


def entra_por_api(email, clave):
    pedido = urllib.request.Request(B + "api/v1/auth/login", method="POST",
                                    data=json.dumps({"email": email, "password": clave}).encode(),
                                    headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(pedido, timeout=30) as respuesta:
            return respuesta.status == 200
    except urllib.error.HTTPError:
        return False


def pagina(br):
    return br.new_context(ignore_https_errors=True, viewport={"width": 1366, "height": 900}).new_page()


def ingresar(br, email, clave, inicio):
    global actual
    pg = actual = pagina(br)
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", clave)
    pg.click("#formLogin button:has-text('Ingresar')")
    pg.wait_for_url("**/" + inicio + "*", timeout=60000)
    return pg


def cambiar_contrasena(pg, actual, nueva, inicio):
    """Primer ingreso: Mi perfil exige elegir una contraseña propia antes de usar el resto."""
    pg.fill("#formPassword\\:actual", actual)
    pg.fill("#formPassword\\:nueva", nueva)
    pg.fill("#formPassword\\:repeticion", nueva)
    pg.click("#formPassword button:has-text('Cambiar contraseña')")
    pg.wait_for_url("**/" + inicio + "*", timeout=60000)


paso, actual = "arrancar el navegador", None


def fallar(error):
    """Dice en qué paso se cortó, en qué página quedó y qué mensaje mostraba (en el pipeline, como anotación)."""
    mensajes = ""
    if actual is not None:
        try:
            mensajes = " | ".join(t.strip() for t in actual.locator(
                ".ui-messages, .ui-growl-message, .ui-message, .mensaje").all_inner_texts() if t.strip())[:300]
        except Exception:  # la página pudo haberse cerrado
            pass
        if not mensajes:  # sin mensajes, al menos lo que dice la pantalla
            try:
                mensajes = "texto de la página: " + " ".join(actual.evaluate("() => document.body.innerText").split())[:300]
            except Exception as sin_texto:  # noqa: BLE001
                mensajes = "no se pudo leer la página (%s)" % str(sin_texto).splitlines()[0][:80]
    detalle = "%s: %s (página: %s; mensajes: %s)" % (paso, (str(error).strip().splitlines() or ["?"])[0][:250],
                                                    actual.url if actual is not None else "-", mensajes or "ninguno")
    if os.environ.get("GITHUB_ACTIONS"):
        print("::error title=Usuarios de demo::" + detalle)
    sys.exit("No se pudo " + detalle)


def faltan():
    todos = [PACIENTE["email"]] + [u["email"] for u in POR_EL_ADMIN]
    return [email for email in todos if not entra_por_api(email, CLAVE_DEMO)]


pendientes = faltan()
if not pendientes:
    print("Los usuarios de demo ya existen: no hay nada que crear.")
    sys.exit(0)

try:
  with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)

    if PACIENTE["email"] in pendientes:
        paso = "registrar al paciente " + PACIENTE["email"]
        pg = actual = pagina(br)
        pg.goto(B + "registro.xhtml")
        for campo in ("nombre", "email", "obraSocial", "nroAfiliado"):
            pg.fill("#formRegistro\\:" + campo, PACIENTE[campo])
        pg.fill("#formRegistro\\:password", CLAVE_DEMO)
        pg.fill("#formRegistro\\:repeticion", CLAVE_DEMO)
        pg.click("#formRegistro button:has-text('Registrarme')")
        pg.wait_for_url("**/login.xhtml*", timeout=60000)
        pg.context.close()
        print("Paciente creado:", PACIENTE["nombre"])

    altas = [u for u in POR_EL_ADMIN if u["email"] in pendientes]
    if altas:
        inicial = os.environ.get("MEDICONECTA_ADMIN_PASSWORD")
        if not inicial:
            sys.exit("Falta MEDICONECTA_ADMIN_PASSWORD (la contraseña inicial del administrador) para dar de alta a "
                     + ", ".join(u["email"] for u in altas))
        nueva = os.environ.get("MEDICONECTA_ADMIN_NUEVA") or "Admin." + secrets.token_hex(8)
        paso = "primer ingreso del administrador por defecto (" + ADMIN + ")"
        adm = ingresar(br, ADMIN, inicial, "perfil.xhtml")
        paso = "cambio de la contraseña inicial del administrador"
        cambiar_contrasena(adm, inicial, nueva, "pagos.xhtml")
        for usuario in altas:
            temporal = "Temporal." + secrets.token_hex(4)
            paso, actual = "alta de " + usuario["email"] + " por el administrador", adm
            adm.goto(B + "usuarios.xhtml")
            adm.select_option("#formAlta\\:rol", usuario["rol"])
            if usuario["rol"] == "PROFESIONAL":
                adm.wait_for_selector("#formAlta\\:matricula")
                adm.fill("#formAlta\\:matricula", usuario["matricula"])
                adm.fill("#formAlta\\:especialidad", usuario["especialidad"])
            adm.fill("#formAlta\\:nombre", usuario["nombre"])
            adm.fill("#formAlta\\:email", usuario["email"])
            adm.fill("#formAlta\\:passwordTemporal", temporal)
            with adm.expect_navigation(timeout=60000):
                adm.click("#formAlta button:has-text('Crear usuario')")
            if adm.locator("[id$='tablaUsuarios'] tr", has_text=usuario["email"]).count() == 0:
                raise RuntimeError("el alta no quedó en la tabla de usuarios")
            paso = "primer ingreso de " + usuario["email"] + " y cambio de su contraseña temporal"
            nuevo = ingresar(br, usuario["email"], temporal, "perfil.xhtml")
            cambiar_contrasena(nuevo, temporal, CLAVE_DEMO, usuario["inicio"])
            nuevo.context.close()
            print("%s creado: %s" % (usuario["rol"].capitalize(), usuario["nombre"]))
        adm.context.close()
    br.close()
except Exception as error:  # noqa: BLE001 - cualquier corte se informa con el paso en que ocurrió
    fallar(error)

siguen = faltan()
if siguen:
    sys.exit("No se pudieron crear: " + ", ".join(siguen))
print("Usuarios de demo listos (contraseña %s)." % CLAVE_DEMO)
