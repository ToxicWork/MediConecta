"""Clave del cifrado de columnas en el credential store de Elytron (diseño, sección 8.6; ADR-0022).

La aplicación cifra con AES-256-GCM el n.º de afiliado y la historia clínica, y lee la clave de la propiedad de
sistema mediconecta.cifrado.clave. Este script hace que esa propiedad no quede en texto plano en ningún lado:

  1. crea el credential store mediconecta-cs (standalone/configuration/mediconecta-cs.cs), con una clave AES propia
     del servidor que genera Elytron;
  2. registra el resolver de expresiones cifradas "mediconecta", que usa esa clave;
  3. cifra la clave de las columnas con ese resolver y la guarda como ${ENC::mediconecta:...} en la propiedad de
     sistema. WildFly la descifra al arrancar; standalone-full.xml sólo tiene la expresión cifrada.

Se puede correr más de una vez (sólo reemplaza la propiedad). Con el servidor APAGADO:

  - Base compartida (Supabase): todos los servidores tienen que usar la MISMA clave, o no van a poder leer lo que
    cifraron los otros. La primera vez se genera y se guarda en un archivo fuera del repositorio para pasarla por un
    canal seguro (el gestor de contraseñas del equipo) y después borrarlo:
        python wildfly/configurar_cifrado.py --generar --guardar-clave C:\\ruta\\fuera\\del\\repo\\clave.txt
    Los demás la configuran con la variable de entorno (la clave nunca va en la línea de comandos):
        set MEDICONECTA_CLAVE_CIFRADO=<la clave>   (Windows)   export MEDICONECTA_CLAVE_CIFRADO=...   (Linux/Mac)
        python wildfly/configurar_cifrado.py
  - Base propia y descartable (el pipeline): python wildfly/configurar_cifrado.py --generar

Si se pierde la clave, los datos cifrados no se pueden recuperar: el archivo mediconecta-cs.cs y la clave en el
gestor de contraseñas son el respaldo. Ni la clave ni el credential store van al repositorio.
Requisitos: Python 3.9+ y WILDFLY_HOME (o --wildfly). Lo verifica pruebas/prueba_datos_protegidos.py.
"""
import argparse, base64, os, re, secrets, subprocess, sys, tempfile

ALMACEN, RESOLVER, ALIAS = "mediconecta-cs", "mediconecta", "expresiones"
PROPIEDAD, VARIABLE = "mediconecta.cifrado.clave", "MEDICONECTA_CLAVE_CIFRADO"
ELYTRON = "/subsystem=elytron"


def cli(wildfly, comandos, estricto=True):
    """Corre los comandos en un WildFly embebido (sin levantar el servidor) y devuelve la salida."""
    ejecutable = os.path.join(wildfly, "bin", "jboss-cli.bat" if os.name == "nt" else "jboss-cli.sh")
    # Archivo temporal sólo legible por el usuario: la clave en claro nunca pasa por la línea de comandos.
    descriptor, archivo = tempfile.mkstemp(suffix=".cli")
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as f:
            f.write("embed-server --server-config=standalone-full.xml\n" + "\n".join(comandos) + "\nstop-embedded-server\n")
        salida = subprocess.run([ejecutable, "--file=" + archivo], capture_output=True, text=True,
                                env=dict(os.environ, NOPAUSE="true"), timeout=300)
    finally:
        os.remove(archivo)
    texto = salida.stdout + salida.stderr
    if estricto and salida.returncode != 0:  # jboss-cli corta y sale con error en el primer comando que falla
        # Sólo las líneas de error: la salida no tiene la clave, pero tampoco hace falta mostrar todo.
        errores = [l.strip() for l in texto.splitlines() if "failure-description" in l or "WFLY" in l]
        sys.exit("Falló la configuración de WildFly:\n  " + "\n  ".join(errores[-5:] or [texto[-500:]]))
    return texto


def clave_de_columnas(generar):
    if generar:
        return base64.b64encode(secrets.token_bytes(32)).decode()
    clave = os.environ.get(VARIABLE, "").strip()
    if not clave:
        sys.exit("Falta la clave: definí la variable de entorno %s con la clave del equipo (32 bytes en Base64), "
                 "o usá --generar si la base es nueva." % VARIABLE)
    try:
        if len(base64.b64decode(clave, validate=True)) != 32:
            raise ValueError
    except ValueError:
        sys.exit("La clave de %s tiene que ser de 32 bytes en Base64." % VARIABLE)
    return clave


def main():
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("--wildfly", default=os.environ.get("WILDFLY_HOME"), help="carpeta de WildFly")
    argumentos.add_argument("--generar", action="store_true", help="generar una clave nueva (base nueva o descartable)")
    argumentos.add_argument("--guardar-clave", metavar="ARCHIVO",
                            help="con --generar: guardar la clave en ese archivo, fuera del repositorio, para pasarla al equipo")
    a = argumentos.parse_args()
    if not a.wildfly or not os.path.isdir(os.path.join(a.wildfly, "bin")):
        sys.exit("No encuentro WildFly: definí WILDFLY_HOME o pasá --wildfly.")
    if a.guardar_clave and not a.generar:
        sys.exit("--guardar-clave sólo tiene sentido con --generar.")
    repositorio = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if a.guardar_clave and os.path.abspath(a.guardar_clave).startswith(repositorio + os.sep):
        sys.exit("La clave no se guarda dentro del repositorio: elegí un archivo afuera.")
    clave = clave_de_columnas(a.generar)

    # 1. Estado actual del resolver de expresiones cifradas.
    estado = cli(a.wildfly, ["%s/expression=encryption:read-resource" % ELYTRON], estricto=False)
    existe_expresion = '"outcome" => "success"' in estado
    tiene_resolver = re.search(r'"name" => "%s"' % RESOLVER, estado) is not None

    # 2. Credential store y resolver (si faltan) y la clave cifrada con él.
    almacen = ("if (outcome != success) of {e}/secret-key-credential-store={a}:read-resource\n"
               "{e}/secret-key-credential-store={a}:add(path={a}.cs, relative-to=jboss.server.config.dir, "
               "create=true, populate=true, key-size=256, default-alias={al})\nend-if").format(e=ELYTRON, a=ALMACEN, al=ALIAS)
    resolver = "{name=%s, credential-store=%s, secret-key=%s}" % (RESOLVER, ALMACEN, ALIAS)
    comandos = [almacen]
    if not existe_expresion:
        comandos.append("%s/expression=encryption:add(resolvers=[%s])" % (ELYTRON, resolver))
    elif not tiene_resolver:
        comandos += ["%s/expression=encryption:list-add(name=resolvers, value=%s)" % (ELYTRON, resolver),
                     "reload --start-mode=admin-only"]
    comandos.append("%s/expression=encryption:create-expression(resolver=%s, clear-text=\"%s\")" % (ELYTRON, RESOLVER, clave))
    salida = cli(a.wildfly, comandos)
    expresion = re.search(r'"expression" => "(\$\{ENC::%s:[A-Za-z0-9+/=]+\})"' % RESOLVER, salida)
    if not expresion:
        sys.exit("Elytron no devolvió la expresión cifrada.")

    # 3. La propiedad de sistema con la expresión cifrada (nunca con la clave en claro).
    valor = expresion.group(1)
    cli(a.wildfly, ["if (outcome == success) of /system-property=%s:read-resource" % PROPIEDAD,
                    '/system-property=%s:write-attribute(name=value, value="%s")' % (PROPIEDAD, valor),
                    "else",
                    '/system-property=%s:add(value="%s")' % (PROPIEDAD, valor),
                    "end-if"])

    if a.guardar_clave:
        with open(a.guardar_clave, "w", encoding="utf-8") as f:
            f.write(clave + "\n")
        print("Clave guardada en %s: pasala por el gestor de contraseñas del equipo y borrá el archivo." % a.guardar_clave)
    print("Listo: %s queda como expresión cifrada de Elytron (credential store %s, resolver %s)."
          % (PROPIEDAD, ALMACEN, RESOLVER))


if __name__ == "__main__":
    main()
