"""Pruebas de seguridad del diseño (tarjetas SEC-* del tablero) sobre la app desplegada.

  W01. Bloqueo tras 5 intentos fallidos: la cuenta rechaza incluso la contraseña correcta, con la misma respuesta
       que ante una contraseña incorrecta (web y API); un ingreso correcto pone el contador en cero; el
       administrativo la desbloquea desde Usuarios.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war desplegado, Google Chrome (o Edge con
NAVEGADOR=msedge) y  pip install playwright
Uso:  python pruebas/prueba_seguridad.py
Deja en la base un paciente de prueba por corrida (seguridad.NNNNN@mediconecta.test). No toca los usuarios de demo.
"""
import json, os, random, re, sys, urllib.error, urllib.request
from playwright.sync_api import sync_playwright

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

B = "http://localhost:8080/mediconecta/"
ADMINISTRATIVO = "carla.admin@mediconecta.test"
NAVEGADOR = os.environ.get("NAVEGADOR", "chrome")
ok = True


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


def login_api(email, clave):
    return http(B + "api/v1/auth/login", "POST", {"email": email, "password": clave})


def sin_fecha(respuesta):
    """Estado y cuerpo de error sin la marca de tiempo, que es lo único que cambia entre dos respuestas."""
    estado, cuerpo = respuesta
    datos = json.loads(cuerpo)
    datos.pop("fecha", None)
    return estado, datos


def pagina(br):
    return br.new_context(viewport={"width": 1366, "height": 900}).new_page()


def login_web(br, email, clave):
    """Devuelve (página, ingresó). Si no ingresó, la página queda en el login con el mensaje de error."""
    pg = pagina(br)
    pg.goto(B + "login.xhtml")
    pg.fill("#formLogin\\:email", email)
    pg.fill("#formLogin\\:password", clave)
    with pg.expect_navigation():
        pg.click("#formLogin button:has-text('Ingresar')")
    return pg, "login.xhtml" not in pg.url


def registrar_paciente(br, email, clave):
    pg = pagina(br)
    pg.goto(B + "registro.xhtml")
    pg.fill("#formRegistro\\:nombre", "Paciente Seguridad")
    pg.fill("#formRegistro\\:email", email)
    pg.fill("#formRegistro\\:password", clave)
    pg.fill("#formRegistro\\:repeticion", clave)
    pg.click("#formRegistro button:has-text('Registrarme')")
    pg.wait_for_url("**/login.xhtml*")
    pg.context.close()


with sync_playwright() as p:
    br = p.chromium.launch(channel=NAVEGADOR, headless=True)
    sufijo = random.randint(10000, 99999)
    email, clave = "seguridad.%d@mediconecta.test" % sufijo, "demo1234"
    registrar_paciente(br, email, clave)

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
    web, ingreso = login_web(br, email, "incorrecta")  # quinto fallo, por la web: los dos caminos suman
    check("W01. el login correcto puso el contador en cero (4 fallos más no bloquean)", not ingreso)
    web.context.close()

    bloqueada = login_api(email, clave)
    check("W01. tras 5 fallos la API rechaza la contraseña correcta (401)", bloqueada[0] == 401)
    check("W01. y responde exactamente lo mismo que ante una contraseña incorrecta", sin_fecha(bloqueada) == sin_fecha(respuesta_incorrecta))
    web, ingreso = login_web(br, email, clave)
    check("W01. la web también rechaza la contraseña correcta, con el mensaje genérico",
          not ingreso and "Credenciales inválidas" in web.content() and "bloque" not in web.inner_text(".ui-messages, .ui-growl, form").lower())
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

    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
