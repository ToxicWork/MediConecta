"""Resume el reporte JSON de OWASP ZAP (resultados-zap/zap-reporte.json) en el resumen del run y en una anotación.

Una fila por alerta, de la más grave a la menos grave, con cuántas veces apareció y un ejemplo. Termina con error si
hay alertas High: el criterio de la cátedra es 0 High y las Medium justificadas una por una (en el informe de
seguridad del documento del QA).
"""
import json, os, sys

REPORTE = os.path.join("resultados-zap", "zap-reporte.json")
RIESGOS = {"3": "High", "2": "Medium", "1": "Low", "0": "Informativa"}

try:
    with open(REPORTE, encoding="utf-8") as f:
        sitios = json.load(f).get("site", [])
except (OSError, ValueError) as e:
    print("::error title=ZAP::no se encontró el reporte de ZAP (%s)" % e)
    sys.exit(1)

alertas = {}
for sitio in sitios:
    for a in sitio.get("alerts", []):
        clave = (a.get("riskcode", "0"), a.get("pluginid", ""), a.get("name") or a.get("alert", ""))
        previa = alertas.setdefault(clave, {"cantidad": 0, "ejemplo": "", "cwe": a.get("cweid", "")})
        previa["cantidad"] += int(a.get("count") or len(a.get("instances", [])) or 1)
        if not previa["ejemplo"] and a.get("instances"):
            instancia = a["instances"][0]
            previa["ejemplo"] = "%s %s" % (instancia.get("method", ""), instancia.get("uri", ""))

totales = {nivel: 0 for nivel in RIESGOS.values()}
filas = []
for (riesgo, plugin, nombre), datos in sorted(alertas.items(), key=lambda x: (-int(x[0][0]), x[0][2])):
    nivel = RIESGOS.get(riesgo, riesgo)
    totales[nivel] += 1
    filas.append("| %s | %s | %s | %s | %d | `%s` |" % (nivel, nombre, plugin, datos["cwe"], datos["cantidad"],
                                                      datos["ejemplo"][:120]))

resumen = ["## Escaneo OWASP ZAP (pasivo y activo autenticado)", "",
           "Alertas distintas: " + ", ".join("%d %s" % (n, nivel) for nivel, n in totales.items()), "",
           "| Riesgo | Alerta | Regla | CWE | Instancias | Ejemplo |", "| --- | --- | --- | --- | --- | --- |"] + filas
with open(os.environ.get("GITHUB_STEP_SUMMARY", os.devnull), "a", encoding="utf-8") as f:
    f.write("\n".join(resumen) + "\n")
print("\n".join(resumen))
texto = ", ".join("%d %s" % (n, nivel) for nivel, n in totales.items())
if totales["High"]:
    print("::error title=ZAP::%s. El criterio es 0 High." % texto)
    sys.exit(1)
print("::notice title=ZAP::%s (0 High)." % texto)
