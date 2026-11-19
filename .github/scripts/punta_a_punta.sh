#!/usr/bin/env bash
# Corre las pruebas de punta a punta de a una (comparten los usuarios de demo y la agenda de la Dra. Ana Lopez),
# deja la salida de cada suite en resultados-e2e/, una tabla en el resumen del run, una anotación por cada
# verificación que falla y un aviso con el resultado de todas. Termina con error si alguna suite falla o se corta.
# Uso (desde la raíz del repositorio, con la aplicación levantada):
#   bash .github/scripts/punta_a_punta.sh [suite ...]     sin argumentos, todas las de pruebas/
set -u
PY=${PYTHON:-python3}
SALIDA=resultados-e2e
RESUMEN=${GITHUB_STEP_SUMMARY:-/dev/null}
SUITES=("$@")
if [ ${#SUITES[@]} -eq 0 ]; then
  # prueba_broker primero (reinicia el servidor) y prueba_monitor después de las que generan avisos.
  SUITES=(prueba_broker prueba_permisos_broker prueba_e2e prueba_seguridad prueba_cancelacion prueba_defectos prueba_inyeccion
          prueba_contrato_api prueba_monitor prueba_entrega4 prueba_hold_vence demo_api_jwt_409 prueba_responsive
          prueba_datos_protegidos prueba_contrato_wsdl)
fi
mkdir -p "$SALIDA"
fallo=0
resultados=""
{
  echo "## Pruebas de punta a punta: ${SUITES[*]}"
  echo
  echo "| Suite | Resultado | Verificaciones | Tiempo |"
  echo "| --- | --- | --- | --- |"
} >> "$RESUMEN"
for nombre in "${SUITES[@]}"; do
  argumentos=()
  # La demo de la API, sin la cancelación final: deja el turno para la demo en vivo.
  [ "$nombre" = demo_api_jwt_409 ] && argumentos=(--sin-cancelar)
  inicio=$(date +%s)
  timeout 1200 "$PY" "pruebas/$nombre.py" "${argumentos[@]}" > "$SALIDA/$nombre.txt" 2>&1
  codigo=$?
  ok=$(grep -c '^OK' "$SALIDA/$nombre.txt" || true)
  fallas=$(grep -c '^FALLA' "$SALIDA/$nombre.txt" || true)
  segundos=$(( $(date +%s) - inicio ))
  # Las líneas AVISO de una suite (observaciones que no la hacen fallar) quedan como advertencias del run.
  { grep '^AVISO' "$SALIDA/$nombre.txt" || true; } | head -10 | while read -r linea; do
    echo "::warning title=$nombre::${linea#AVISO }"
  done
  if [ "$codigo" -ne 0 ] || [ "$fallas" -ne 0 ]; then
    fallo=1
    resultado="❌"
    { grep '^FALLA' "$SALIDA/$nombre.txt" || true; } | head -10 | while read -r linea; do
      echo "::error title=$nombre::$linea"
    done
    if [ "$fallas" -eq 0 ]; then
      # Se cortó sin verificaciones fallidas: el final de su salida (el traceback) en una sola anotación.
      final=$({ grep -v '^[[:space:]]*$' "$SALIDA/$nombre.txt" | tail -8 | cut -c1-250; } || true)
      echo "::error title=$nombre::terminó con código $codigo%0A$(echo "$final" | sed ':a;N;$!ba;s/%/%25/g;s/\n/%0A/g')"
    fi
  else
    resultado="✅"
  fi
  echo "| $nombre | $resultado | $ok OK, $fallas fallas | $segundos s |" >> "$RESUMEN"
  echo "$nombre: código $codigo, $ok OK, $fallas fallas, $segundos s"
  resultados="$resultados$resultado $nombre: $ok OK, $fallas fallas, $segundos s%0A"
done
# Una sola anotación con todas las suites: el resultado se ve en la página del run sin abrir el log.
echo "::notice title=Punta a punta::${resultados%\%0A}"
exit "$fallo"
