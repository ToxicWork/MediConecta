"""
Permisos por destino en el broker (documento de diseño, sección 8.5; wildfly/configurar-permisos-broker.cli y
ADR-0020).

Un cliente JMS de afuera de la aplicación (pruebas/ClienteJmsExterno.java, con el cliente de WildFly) se conecta al
broker por el puerto HTTP, como cualquier cliente remoto, e intenta cada operación sobre los destinos de MediConecta:

  1. Sin usuario, el broker no lo deja entrar.
  2. Con un usuario de aplicación de WildFly con el rol guest (el de los clientes remotos), entra pero no puede
     publicar en el tópico de turnos, suscribirse (tampoco con suscripción durable), leer ni mandar a la cola de
     autorizaciones, ni leer la DLQ. Sin el script, ese rol podía todo eso: inventar un TURNO_CONFIRMADO o escuchar
     todos los eventos de turnos.
  3. La configuración queda como dice el script, y la aplicación sigue conectada al broker: el tópico conserva sus
     tres suscripciones durables y la cola sus consumidores. El circuito completo lo cubren prueba_broker y
     prueba_e2e.

Necesita WILDFLY_HOME: da de alta el usuario de prueba con add-user y consulta el broker con jboss-cli. El usuario
(cliente-externo) recibe una clave al azar en cada corrida, que no se imprime.
"""
import os, re, secrets, subprocess, sys

WILDFLY_HOME = os.environ.get("WILDFLY_HOME", "")
HOST_PUERTO = os.environ.get("MC_BROKER", "127.0.0.1:8080")
CLIENTE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ClienteJmsExterno.java")
USUARIO = "cliente-externo"
SERVIDOR = "/subsystem=messaging-activemq/server=default"
OPERACIONES = ("publicar en el tópico turnos", "suscribirse al tópico turnos",
               "crear una suscripción durable al tópico turnos", "leer la cola autorizaciones",
               "mandar a la cola autorizaciones", "leer la DLQ")
ok = True


def check(nombre, condicion):
    global ok
    ok &= bool(condicion)
    print(("OK   " if condicion else "FALLA"), nombre)


def herramienta(nombre):
    return os.path.join(WILDFLY_HOME, "bin", nombre + (".bat" if os.name == "nt" else ".sh"))


def jboss_cli(comando):
    return subprocess.run([herramienta("jboss-cli"), "--connect", "--command=" + comando], capture_output=True,
                          text=True, timeout=90, stdin=subprocess.DEVNULL, env={**os.environ, "NOPAUSE": "true"}).stdout


def resultado(salida):
    """El valor de "result" de una respuesta de jboss-cli, como texto (None si la operación falló)."""
    if '"outcome" => "success"' not in salida:
        return None
    m = re.search(r'"result" => (.*)\n}', salida, re.S)
    return m.group(1).strip() if m else ""


def cliente(usuario=None, clave=None):
    """Corre el cliente externo y devuelve {operación: (permitido, motivo)}."""
    env = {k: v for k, v in os.environ.items() if not k.startswith("CLIENTE_JMS_")}
    if usuario:
        env.update(CLIENTE_JMS_USUARIO=usuario, CLIENTE_JMS_CLAVE=clave)
    jar = os.path.join(WILDFLY_HOME, "bin", "client", "jboss-client.jar")
    salida = subprocess.run(["java", "-Dstdout.encoding=UTF-8", "-cp", jar, CLIENTE, HOST_PUERTO], capture_output=True,
                            timeout=180, stdin=subprocess.DEVNULL, env=env).stdout.decode("utf-8", "replace")
    respuestas = {}
    for linea in salida.splitlines():
        m = re.match(r"(PERMITIDO|DENEGADO|ERROR) ([^:]+)(?:: (.*))?$", linea.strip())
        if m:
            respuestas[m.group(2)] = (m.group(1) == "PERMITIDO", m.group(1) + " " + (m.group(3) or ""))
    if not respuestas:
        print(salida[-1500:])
    return respuestas


if not WILDFLY_HOME:
    sys.exit("Definí WILDFLY_HOME: la prueba da de alta un usuario con add-user y consulta el broker con jboss-cli.")

# ---- 1. Sin usuario ---------------------------------------------------------------------------------------------
print("-- 1. Un cliente sin usuario")
anonimo = cliente()
permitido, motivo = anonimo.get("conectarse", (True, "sin respuesta del cliente"))
check("1. el broker no deja entrar a un cliente sin usuario (%s)" % motivo[:110],
      not permitido and "AMQ229031" in motivo)

# ---- 2. Usuario de aplicación con el rol guest -------------------------------------------------------------------
print("-- 2. Un usuario de aplicación de WildFly con el rol guest")
clave = "Cx-" + secrets.token_hex(12) + "-9"
alta = subprocess.run([herramienta("add-user"), "-a", "-u", USUARIO, "-p", clave, "-g", "guest", "-s"],
                      capture_output=True, text=True, timeout=120, stdin=subprocess.DEVNULL,
                      env={**os.environ, "NOPAUSE": "true"})
recargado = resultado(jboss_cli("/subsystem=elytron/properties-realm=ApplicationRealm:load")) is not None
check("2. add-user da de alta (o actualiza) al usuario %s con el rol guest" % USUARIO, alta.returncode == 0 and recargado)
externo = cliente(USUARIO, clave)
permitido, motivo = externo.get("conectarse", (False, "sin respuesta del cliente"))
check("2. con usuario y clave válidos, el cliente entra al broker (%s)" % motivo.strip(), permitido)
for operacion in OPERACIONES:
    permitido, motivo = externo.get(operacion, (True, "sin respuesta del cliente"))
    check("2. no puede %s (%s)" % (operacion, motivo[:120]),
          not permitido and "does not have permission" in motivo)

# ---- 3. Configuración y aplicación -------------------------------------------------------------------------------
print("-- 3. La configuración del broker y la aplicación")
check("3. el rol guest ya no tiene permisos sobre todos los destinos (#)",
      resultado(jboss_cli(SERVIDOR + "/security-setting=#/role=guest:read-resource")) is None)
for destino, roles in (("jms.topic.turnos", ("aplicacion", "consumidor")),
                       ("jms.queue.autorizaciones", ("aplicacion", "consumidor"))):
    hijos = resultado(jboss_cli(SERVIDOR + "/security-setting=%s:read-children-names(child-type=role)" % destino)) or ""
    check("3. %s tiene sus roles propios (%s)" % (destino, hijos.replace("\n", " ")),
          all('"%s"' % rol in hijos for rol in roles) and '"guest"' not in hijos)
publica = resultado(jboss_cli(SERVIDOR + "/security-setting=jms.topic.turnos/role=consumidor:read-attribute(name=send)"))
check("3. el rol consumidor no puede publicar en el tópico de turnos", publica == "false")
durables = resultado(jboss_cli(SERVIDOR + "/jms-topic=turnos:read-attribute(name=durable-subscription-count)"))
check("3. el tópico conserva las 3 suscripciones durables de la aplicación (%s)" % durables, durables == "3")
consumidores = resultado(jboss_cli(SERVIDOR + "/jms-queue=autorizaciones:read-attribute(name=consumer-count)"))
check("3. la cola de autorizaciones conserva sus consumidores (%s)" % consumidores,
      consumidores is not None and consumidores.isdigit() and int(consumidores) >= 1)

print("\nRESULTADO:", "TODO OK" if ok else "HAY FALLAS")
sys.exit(0 if ok else 1)
