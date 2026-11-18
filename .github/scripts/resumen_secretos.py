"""Resumen de los hallazgos de gitleaks para el pipeline, sin mostrar nunca el valor encontrado.

Uso: python3 .github/scripts/resumen_secretos.py gitleaks.json <commits revisados>

Escribe una anotación por hallazgo (::error, con la regla, el archivo, la línea y el commit, que GitHub muestra en la
página del run) y agrega a $GITHUB_STEP_SUMMARY una tabla con lo mismo. El reporte viene redactado (--redact): ni el
secreto ni la línea que lo contiene figuran en él, y este script tampoco los usa.
"""
import json
import os
import sys

from rutas import dentro_del_repo


def main(ruta, commits):
    try:
        with open(dentro_del_repo(ruta), encoding="utf-8") as archivo:
            hallazgos = json.load(archivo)
    except (OSError, ValueError):
        hallazgos = None
    resumen = ["## Secretos en el historial (gitleaks 8.30.1)", ""]
    if hallazgos is None:
        resumen.append("gitleaks no generó el reporte: ver el log del paso anterior.")
    elif not hallazgos:
        resumen.append("Commits revisados: %s, de todas las ramas. Sin hallazgos." % commits)
    else:
        resumen += ["Commits revisados: %s, de todas las ramas. **Hallazgos: %d.**" % (commits, len(hallazgos)), "",
                    "| Regla | Archivo | Línea | Commit | Fecha |", "|---|---|---|---|---|"]
        # GitHub muestra hasta 10 anotaciones de error por paso: la primera trae todas las huellas juntas (el formato
        # de .gitleaksignore), así ninguna queda afuera; después va una por hallazgo.
        print("::error title=gitleaks: %d hallazgos::%s" % (len(hallazgos), "%0A".join(h.get("Fingerprint", "?") for h in hallazgos)))
        for h in hallazgos:
            commit = (h.get("Commit") or "")[:8]
            fecha = (h.get("Date") or "")[:10]
            resumen.append("| %s | %s | %s | %s | %s |" % (h.get("RuleID"), h.get("File"), h.get("StartLine"), commit, fecha))
            print("::error file=%s,line=%s,title=gitleaks %s::%s (commit %s del %s)"
                  % (h.get("File"), h.get("StartLine"), h.get("RuleID"), h.get("Description"), commit, fecha))
    with open(os.environ.get("GITHUB_STEP_SUMMARY", os.devnull), "a", encoding="utf-8") as salida:
        salida.write("\n".join(resumen) + "\n")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
