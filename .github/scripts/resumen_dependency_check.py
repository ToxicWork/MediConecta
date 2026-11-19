"""Resumen de OWASP Dependency-Check para el resumen del run, y una anotación con lo que hay que mirar.

Lee el reporte JSON y lista las dependencias con CVE de CVSS 7 o más (las que hacen fallar el trabajo) y cuántas más
bajas hay. El resumen va a GITHUB_STEP_SUMMARY (o a la salida, fuera del pipeline); la anotación, a la salida.
Uso: python3 resumen_dependency_check.py target/dependency-check-report.json
"""
import json, os, sys

UMBRAL = 7.0
DESTINO = os.environ.get("GITHUB_STEP_SUMMARY")


def escribir(texto=""):
    if DESTINO:
        with open(DESTINO, "a", encoding="utf-8") as resumen:
            resumen.write(texto + "\n")
    else:
        print(texto)


def anotar(titulo, texto):
    print("::error title=%s::%s" % (titulo, texto.replace("%", "%25").replace("\n", "%0A")))


def puntaje(vulnerabilidad):
    for clave in ("cvssv3", "cvssv2"):
        datos = vulnerabilidad.get(clave) or {}
        valor = datos.get("baseScore") or datos.get("score")
        if valor is not None:
            return float(valor)
    return {"CRITICAL": 9.0, "HIGH": 7.0, "MEDIUM": 5.0, "LOW": 2.0}.get(str(vulnerabilidad.get("severity", "")).upper(), 0.0)


def main(ruta):
    escribir("## Dependencias contra la NVD (OWASP Dependency-Check)\n")
    if not os.path.exists(ruta):
        escribir("No se generó el reporte: el análisis no llegó a terminar (ver el log del trabajo).")
        anotar("Dependency-Check", "No se generó el reporte: ver el log del trabajo")
        return
    with open(ruta, encoding="utf-8") as archivo:
        dependencias = json.load(archivo).get("dependencies", [])
    altas, bajas = [], 0
    for dependencia in dependencias:
        for vulnerabilidad in dependencia.get("vulnerabilities", []):
            valor = puntaje(vulnerabilidad)
            if valor >= UMBRAL:
                altas.append((dependencia.get("fileName", "?"), vulnerabilidad.get("name", "?"), valor))
            else:
                bajas += 1
    suprimidas = sum(len(d.get("suppressedVulnerabilities", [])) for d in dependencias)
    escribir("| Dependencias analizadas | CVE de CVSS 7 o más | CVE más bajas | Suprimidas (justificadas) |")
    escribir("|---|---|---|---|")
    escribir("| %d | %d | %d | %d |\n" % (len(dependencias), len(altas), bajas, suprimidas))
    if not altas:
        escribir("Ninguna dependencia del WAR tiene una CVE alta o crítica sin justificar.")
        return
    escribir("| Dependencia | CVE | CVSS |\n|---|---|---|")
    for nombre, cve, valor in sorted(altas, key=lambda a: -a[2]):
        escribir("| %s | %s | %.1f |" % (nombre, cve, valor))
    anotar("Dependency-Check", "%d CVE de CVSS 7 o más\n" % len(altas)
           + "\n".join("%s: %s (%.1f)" % a for a in altas[:15]))


if __name__ == "__main__":
    main(sys.argv[1])
