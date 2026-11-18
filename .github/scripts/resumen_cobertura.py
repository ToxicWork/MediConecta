"""Resumen de la cobertura de JaCoCo para el pipeline.

Uso: python3 .github/scripts/resumen_cobertura.py >> "$GITHUB_STEP_SUMMARY" (desde la raíz del repositorio)

Escribe en Markdown la cobertura de líneas y de ramas de la capa de negocio y dominio (paquetes modelo y negocio,
la que miden los umbrales del Eje B), la de todo el código del equipo y el detalle por paquete.
"""
import csv
import os
from collections import defaultdict


# Siempre el reporte que deja mvn verify: el script no recibe rutas (Sonar pythonsecurity:S8707).
REPORTE = os.path.join("target", "site", "jacoco", "jacoco.csv")
RAIZ = "ar.edu.mediconecta"
ALCANCE = ("modelo", "negocio")
OBJETIVO_LINEAS = 80.0
OBJETIVO_RAMAS = 70.0


def porcentaje(cubiertas, perdidas):
    total = cubiertas + perdidas
    return 100.0 * cubiertas / total if total else 100.0


def leer(ruta):
    """Por paquete de primer nivel: [líneas cubiertas, líneas sin cubrir, ramas cubiertas, ramas sin cubrir]."""
    por_paquete = defaultdict(lambda: [0, 0, 0, 0])
    with open(ruta, newline="", encoding="utf-8") as archivo:
        for fila in csv.DictReader(archivo):
            paquete = fila["PACKAGE"]
            relativo = paquete[len(RAIZ) + 1:] if paquete.startswith(RAIZ + ".") else paquete
            cuenta = por_paquete[relativo.split(".")[0]]
            cuenta[0] += int(fila["LINE_COVERED"])
            cuenta[1] += int(fila["LINE_MISSED"])
            cuenta[2] += int(fila["BRANCH_COVERED"])
            cuenta[3] += int(fila["BRANCH_MISSED"])
    return por_paquete


def sumar(por_paquete, paquetes):
    total = [0, 0, 0, 0]
    for paquete in paquetes:
        total = [a + b for a, b in zip(total, por_paquete[paquete])]
    return total


def fila(nombre, cuenta, objetivos=None):
    lineas = porcentaje(cuenta[0], cuenta[1])
    ramas = porcentaje(cuenta[2], cuenta[3])
    if objetivos:
        marca_l = " ✅" if lineas >= objetivos[0] else " ❌"
        marca_r = " ✅" if ramas >= objetivos[1] else " ❌"
    else:
        marca_l = marca_r = ""
    return "| %s | %.1f %% (%d de %d)%s | %.1f %% (%d de %d)%s |" % (
        nombre, lineas, cuenta[0], cuenta[0] + cuenta[1], marca_l, ramas, cuenta[2], cuenta[2] + cuenta[3], marca_r)


def main(ruta):
    por_paquete = leer(ruta)
    print("## Cobertura de las pruebas unitarias (JaCoCo)\n")
    print("| Alcance | Líneas | Ramas |")
    print("|---|---|---|")
    print(fila("**Negocio y dominio** (objetivo: %d %% de líneas y %d %% de ramas)" % (OBJETIVO_LINEAS, OBJETIVO_RAMAS),
               sumar(por_paquete, ALCANCE), (OBJETIVO_LINEAS, OBJETIVO_RAMAS)))
    print(fila("Todo el código del equipo", sumar(por_paquete, por_paquete.keys())))
    print("\n<details><summary>Por paquete</summary>\n")
    print("| Paquete | Líneas | Ramas |")
    print("|---|---|---|")
    for paquete in sorted(por_paquete):
        print(fila(paquete, por_paquete[paquete]))
    print("\n</details>\n")


if __name__ == "__main__":
    main(REPORTE)
