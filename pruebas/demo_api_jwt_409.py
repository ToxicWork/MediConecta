"""Demo por consola de la seguridad base y del patrón State de la entrega del 16/11.

Muestra, pedido por pedido, lo que hay que defender en vivo:

  1. Sin token la API responde 401.
  2. El login entrega un JWT (se muestran su cabecera y su contenido decodificados).
  3. Con el token se accede; con un token adulterado, 401.
  4. Control de acceso por rol: el administrativo tiene token válido pero recibe 403.
  5. Patrón State: cancelar un turno confirmado da 200 y cancelarlo otra vez da 409.

Requisitos: la app desplegada en https://localhost:8443 y los usuarios de demo (ver SETUP.md). Sólo usa la
biblioteca estándar de Python.

Uso:  python pruebas/demo_api_jwt_409.py            cancela el turno confirmado más lejano del paciente de demo
      python pruebas/demo_api_jwt_409.py 57         cancela el turno 57
      python pruebas/demo_api_jwt_409.py --sin-cancelar   sólo los pasos 1 a 4 (no modifica datos)

Atención: el paso 5 cancela de verdad un turno del paciente de demo.
"""
import base64, json, sys, urllib.error, urllib.request
import ssl
# MediConecta corre por HTTPS con el certificado autofirmado que WildFly genera para localhost (SEC-W04):
# las pruebas lo aceptan sin validarlo. Sólo sirve para el entorno local.
urllib.request.install_opener(urllib.request.build_opener(
    urllib.request.HTTPSHandler(context=ssl._create_unverified_context())))

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:
    pass

API = "https://localhost:8443/mediconecta/api/v1"
PACIENTE, ADMINISTRATIVO, CLAVE = "juan.perez@mediconecta.test", "carla.admin@mediconecta.test", "demo1234"
ok = True


def pedir(metodo, ruta, token=None, cuerpo=None, mostrar=True):
    pedido = urllib.request.Request(API + ruta, method=metodo, data=None if cuerpo is None else json.dumps(cuerpo).encode())
    if cuerpo is not None:
        pedido.add_header("Content-Type", "application/json")
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(pedido, timeout=15) as r:
            estado, texto, cabeceras = r.status, r.read().decode("utf-8"), r.headers
    except urllib.error.HTTPError as e:
        estado, texto, cabeceras = e.code, e.read().decode("utf-8"), e.headers
    if mostrar:
        print("\n> %s /api/v1%s%s" % (metodo, ruta, "   [Authorization: Bearer %s...]" % token[:18] if token else ""))
        if cuerpo is not None:
            print("> " + json.dumps({k: ("********" if k == "password" else v) for k, v in cuerpo.items()}))
        print("< HTTP %d" % estado + ("   WWW-Authenticate: " + cabeceras["WWW-Authenticate"] if cabeceras.get("WWW-Authenticate") else ""))
        print("< " + (texto if len(texto) <= 330 else texto[:330] + " ..."))
    return estado, texto


def esperado(descripcion, condicion):
    global ok
    ok &= bool(condicion)
    print(("   OK    " if condicion else "   FALLA ") + descripcion)


def decodificar(parte):
    return base64.urlsafe_b64decode(parte + "=" * (-len(parte) % 4)).decode("utf-8")


def b64url(texto):
    return base64.urlsafe_b64encode(texto.encode()).decode().rstrip("=")


print("=== 1. Sin token ===")
estado, _ = pedir("GET", "/turnos")
esperado("401 Unauthorized", estado == 401)

print("\n=== 2. Login: email y contraseña por un JWT ===")
estado, _ = pedir("POST", "/auth/login", cuerpo={"email": PACIENTE, "password": "incorrecta"})
esperado("contraseña incorrecta: 401", estado == 401)
estado, texto = pedir("POST", "/auth/login", cuerpo={"email": PACIENTE, "password": CLAVE})
esperado("credenciales válidas: 200 con el token", estado == 200)
token = json.loads(texto)["token"]
cabecera, contenido, firma = token.split(".")
print("   cabecera  : " + decodificar(cabecera))
print("   contenido : " + decodificar(contenido))
print("   firma     : %s (HMAC-SHA256 con el secreto del servidor)" % firma)

print("\n=== 3. Con el token ===")
estado, texto = pedir("GET", "/turnos", token)
esperado("200 con los turnos del paciente", estado == 200)
turnos = json.loads(texto) if estado == 200 else []
datos = json.loads(decodificar(contenido))
adulterado = cabecera + "." + b64url(json.dumps(dict(datos, rol="ADMINISTRATIVO"), separators=(",", ":"))) + "." + firma
estado, _ = pedir("GET", "/turnos", adulterado)
esperado("token con el rol cambiado a mano (la firma ya no coincide): 401", estado == 401)

print("\n=== 4. Control de acceso por rol ===")
_, texto = pedir("POST", "/auth/login", cuerpo={"email": ADMINISTRATIVO, "password": CLAVE}, mostrar=False)
token_admin = json.loads(texto)["token"]
estado, _ = pedir("GET", "/turnos", token_admin)
esperado("token válido de ADMINISTRATIVO en un recurso de PACIENTE/PROFESIONAL: 403", estado == 403)

if "--sin-cancelar" not in sys.argv:
    print("\n=== 5. Patrón State: transición inválida = 409 ===")
    elegido = next((a for a in sys.argv[1:] if a.isdigit()), None)
    if elegido is None:
        confirmados = [t for t in turnos if t["estado"] == "CONFIRMADO"]
        elegido = confirmados[0]["id"] if confirmados else None
    if elegido is None:
        print("   El paciente de demo no tiene turnos confirmados: reservá y confirmá uno desde la web y repetí.")
        ok = False
    else:
        estado, texto = pedir("POST", "/turnos/%s/cancelacion" % elegido, token)
        esperado("CONFIRMADO -> CANCELADO: 200", estado == 200 and json.loads(texto)["estado"] == "CANCELADO")
        estado, texto = pedir("POST", "/turnos/%s/cancelacion" % elegido, token)
        esperado("cancelar un turno ya cancelado: 409 transicion_invalida", estado == 409 and "transicion_invalida" in texto)
        estado, _ = pedir("POST", "/turnos/%s/cancelacion" % elegido, token_admin)
        esperado("el administrativo no puede cancelar: 403", estado == 403)

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
