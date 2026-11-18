"""Resultado de una corrida de la suite, prueba por prueba, a partir de los reportes XML de Surefire.

Uso: python3 .github/scripts/resultado_suite.py > corrida-1.txt (desde la raíz del repositorio)

La primera línea es el total («100 pruebas: 100 pasaron, 0 fallaron, 0 con error, 0 omitidas») y las siguientes, cada
prueba con su resultado, ordenadas: dos corridas dieron lo mismo si los archivos son idénticos.
"""
import glob
import os
import xml.etree.ElementTree as ET

# Siempre los reportes que deja Surefire: el script no recibe rutas (Sonar pythonsecurity:S8707).
CARPETA = os.path.join("target", "surefire-reports")


def resultado(caso):
    for estado in ("failure", "error", "skipped"):
        if caso.find(estado) is not None:
            return estado
    return "ok"


def main(carpeta):
    pruebas = []
    for reporte in glob.glob(os.path.join(carpeta, "TEST-*.xml")):
        for caso in ET.parse(reporte).getroot().iter("testcase"):
            pruebas.append(("%s.%s" % (caso.get("classname"), caso.get("name")), resultado(caso)))
    pruebas.sort()
    cuenta = {estado: sum(1 for _, r in pruebas if r == estado) for estado in ("ok", "failure", "error", "skipped")}
    print("%d pruebas: %d pasaron, %d fallaron, %d con error, %d omitidas"
          % (len(pruebas), cuenta["ok"], cuenta["failure"], cuenta["error"], cuenta["skipped"]))
    for nombre, r in pruebas:
        print("%s %s" % (r, nombre))


if __name__ == "__main__":
    main(CARPETA)
