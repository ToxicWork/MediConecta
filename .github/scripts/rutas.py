"""Rutas que reciben los scripts del pipeline por línea de comandos (Sonar pythonsecurity:S8707)."""
import os
import sys


def dentro_del_repo(ruta):
    """La ruta, ya resuelta, si queda dentro del directorio de trabajo (el checkout del repositorio); si no, se corta.

    Los scripts sólo leen reportes que deja el build (target/...), nunca un archivo de afuera del repositorio.
    """
    base = os.path.realpath(os.getcwd())
    completa = os.path.realpath(ruta)
    if os.path.commonpath([base, completa]) != base:
        sys.exit("La ruta tiene que estar dentro del repositorio: " + ruta)
    return completa
