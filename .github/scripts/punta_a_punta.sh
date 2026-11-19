#!/usr/bin/env bash
# Corre las pruebas de punta a punta de a una (comparten los usuarios de demo y la agenda de la Dra. Ana Lopez),
# deja la salida de cada suite en resultados-e2e/, una tabla en el resumen del run y una anotación por cada
# verificación que falla. Termina con error si alguna suite falla o se corta.
# Uso (desde la raíz del repositorio, con la aplicación levantada): bash .github/scripts/punta_a_punta.sh
set -u
PY=${PYTHON:-python3}
SALIDA=resultados-e2e
RESUMEN=${GITHUB_STEP_SUMMARY:-/dev/null}
mkdir -p "$SALIDA"
fallo=0
{
  echo "## Pruebas de punta a punta"
  echo
  echo "| Suite | Resultado | Verificaciones | Tiempo |"
  echo "| --- | --- | --- | --- |"
} >> "$RESUMEN"
for prueba in prueba_e2e prueba_seguridad prueba_cancelacion prueba_defectos prueba_monitor prueba_entrega4 \
              prueba_hold_vence "demo_api_jwt_409 --sin-cancelar"; do
  nombre=${prueba%% *}
  argumentos=${prueba#"$nombre"}
  inicio=$(date +%s)
  # shellcheck disable=SC2086  # los argumentos de la suite van separados
  timeout 1200 "$PY" "pruebas/$nombre.py" $argumentos > "$SALIDA/$nombre.txt" 2>&1
  codigo=$?
  ok=$(grep -c '^OK' "$SALIDA/$nombre.txt" || true)
  fallas=$(grep -c '^FALLA' "$SALIDA/$nombre.txt" || true)
  segundos=$(( $(date +%s) - inicio ))
  if [ "$codigo" -ne 0 ] || [ "$fallas" -ne 0 ]; then
    fallo=1
    resultado="❌"
    { grep '^FALLA' "$SALIDA/$nombre.txt" || true; } | head -10 | while read -r linea; do
      echo "::error title=$nombre::$linea"
    done
    if [ "$fallas" -eq 0 ]; then
      echo "::error title=$nombre::terminó con código $codigo: $(tail -1 "$SALIDA/$nombre.txt")"
    fi
  else
    resultado="✅"
  fi
  echo "| $nombre | $resultado | $ok OK, $fallas fallas | $segundos s |" >> "$RESUMEN"
  echo "$nombre: código $codigo, $ok OK, $fallas fallas, $segundos s"
  resultados="${resultados:-}$resultado $nombre: $ok OK, $fallas fallas, $segundos s%0A"
done
# Una sola anotación con todas las suites: el resultado se ve en la página del run sin abrir el log.
echo "::notice title=Punta a punta::${resultados%\%0A}"
exit "$fallo"
