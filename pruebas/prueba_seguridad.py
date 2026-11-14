"""Pruebas de seguridad del diseño (tarjetas SEC-* del tablero) sobre la app desplegada.

  W01. Bloqueo tras 5 intentos fallidos: la cuenta rechaza incluso la contraseña correcta, con la misma respuesta
       que ante una contraseña incorrecta (web y API); un ingreso correcto pone el contador en cero; el
       administrativo la desbloquea desde Usuarios.
  W04. Cookie de sesión con HttpOnly, Secure y SameSite=Lax; toda la app por HTTPS (http redirige a https, también
       la API) y la cookie no es legible desde JavaScript.

Requisitos: WildFly levantado (perfil standalone-full) con mediconecta.war desplegado, Google Chrome (o Edge con
NAVEGADOR=msedge) y  pip install playwright
Uso:  python pruebas/prueba_seguridad.py
Deja en la base un paciente de prueba por corrida (seguridad.NNNNN@mediconecta.test). No toca los usuarios de demo.
"""
import http.client as cliente_http
import json, os, random, re, sys, urllib.error, urllib.request
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

    br.close()

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
