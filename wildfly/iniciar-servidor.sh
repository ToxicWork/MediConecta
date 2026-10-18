#!/usr/bin/env bash
# Levanta WildFly con la app MediConecta ya desplegada (deployments/mediconecta.war).
# Uso: ./wildfly/iniciar-servidor.sh
# Usa el perfil standalone-full.xml: es el que trae el broker ActiveMQ Artemis (mensajería JMS).
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
WILDFLY_HOME="${WILDFLY_HOME:-$HOME/Downloads/wildfly-41.0.1.Final}"

if [ ! -x "$WILDFLY_HOME/bin/standalone.sh" ]; then
  echo "No encuentro WildFly en $WILDFLY_HOME. Seteá WILDFLY_HOME." >&2
  exit 1
fi

echo "JAVA_HOME=$JAVA_HOME"
echo "WILDFLY_HOME=$WILDFLY_HOME"
echo "App: http://localhost:8080/mediconecta/login.xhtml"
exec "$WILDFLY_HOME/bin/standalone.sh" -c standalone-full.xml
