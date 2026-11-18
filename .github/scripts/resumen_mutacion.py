"""Resumen de las pruebas de mutación de PIT para el pipeline.

Uso: python3 .github/scripts/resumen_mutacion.py target/pit-reports/mutations.xml >> "$GITHUB_STEP_SUMMARY"

Escribe en Markdown el puntaje de mutación del paquete de dominio (mutantes detectados sobre el total, el que mide el
umbral del Eje B), la fortaleza de las pruebas (detectados sobre los que alguna prueba ejecuta) y el detalle por clase.
"""
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict

OBJETIVO = 60.0


def main(ruta):
    mutantes = ET.parse(ruta).getroot().findall("mutation")
    estados = Counter(m.get("status") for m in mutantes)
    detectados = sum(1 for m in mutantes if m.get("detected") == "true")
    total = len(mutantes)
    sin_cobertura = estados.get("NO_COVERAGE", 0)
    puntaje = 100.0 * detectados / total if total else 100.0
    fortaleza = 100.0 * detectados / (total - sin_cobertura) if total - sin_cobertura else 100.0

    print("## Pruebas de mutación sobre el dominio (PIT)\n")
    print("| Medida | Valor |")
    print("|---|---|")
    print("| **Puntaje de mutación** (objetivo: %d %%) | %.1f %% (%d de %d)%s |"
          % (OBJETIVO, puntaje, detectados, total, " ✅" if puntaje >= OBJETIVO else " ❌"))
    print("| Fortaleza de las pruebas (sin contar el código que ninguna prueba ejecuta) | %.1f %% |" % fortaleza)
    print("| Estados | %s |" % ", ".join("%s: %d" % par for par in sorted(estados.items())))

    por_clase = defaultdict(lambda: [0, 0])
    for m in mutantes:
        clase = m.findtext("mutatedClass").rsplit(".", 1)[-1]
        por_clase[clase][1] += 1
        if m.get("detected") == "true":
            por_clase[clase][0] += 1
    print("\n<details><summary>Por clase (de menor a mayor puntaje)</summary>\n")
    print("| Clase | Detectados |")
    print("|---|---|")
    for clase, (det, tot) in sorted(por_clase.items(), key=lambda par: par[1][0] / par[1][1]):
        print("| %s | %.0f %% (%d de %d) |" % (clase, 100.0 * det / tot, det, tot))
    print("\n</details>\n")


if __name__ == "__main__":
    main(sys.argv[1])
