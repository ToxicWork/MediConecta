"""Las tarjetas del tablero para G5: un caso de prueba o un defecto por tarjeta, con su estado real, su HU y sin
responsable (el equipo los asigna). Escribe tarjetas.json, que se carga en Trello por su API.

Lista según el estado: un caso que pasó en el pipeline va a TERMINADO, uno manual o sin ejecutar a POR HACER y uno
que falló a EN PROGRESO. Un defecto cerrado o aceptado va a TERMINADO, uno resuelto en una rama a EN REVISION y uno
abierto a POR HACER. Uso:  python docs/qa/tarjetas_tablero.py
"""
import json, pathlib, sys

AQUI = pathlib.Path(__file__).parent
sys.path.insert(0, str(AQUI))
from datos_qa import CASOS  # noqa: E402
from datos_defectos import DEFECTOS  # noqa: E402
from generar_documento_qa import resultado  # noqa: E402  (también regenera documento-qa.html)

R = json.loads((AQUI / "resultados.json").read_text(encoding="utf-8"))
tarjetas = []
for c in CASOS:
    res = resultado(c["resultado"])
    lista = "TERMINADO" if res.startswith("Pasó") else "EN PROGRESO" if res.startswith("Falló") else "POR HACER"
    desc = "\n".join([
        "**Caso de prueba** · %s · prioridad %s · nivel %s" % (c["tipo"], c["prioridad"], c["nivel"]),
        "**HU:** %s" % ", ".join(c["hu"]),
        "**Responsable:** a asignar por el equipo",
        "", "**Precondición:** %s" % c["pre"], "", "**Datos:** %s" % c["datos"], "", "**Pasos:** %s" % c["pasos"],
        "", "**Resultado esperado:** %s" % c["esperado"],
        "", "**Automatizado:** %s" % ("sí, %s" % c["prueba"] if c["prueba"] != "Manual" else "no (manual)"),
        "**Último resultado:** %s (corrida %s, %s)" % (res, R["corrida"], R["fecha_corrida"]),
        "**Defectos relacionados:** %s" % (", ".join(c["defectos"]) or "ninguno"),
    ])
    tarjetas.append({"nombre": "%s · %s" % (c["id"], c["titulo"]), "lista": lista, "desc": desc, "etiqueta": "Caso de prueba"})

CON_TARJETA = set("D-%02d" % n for n in range(13, 41)) | {"D-43"}
for d in DEFECTOS:
    if d["id"] in CON_TARJETA:
        continue
    lista = {"Cerrado": "TERMINADO", "Limitación aceptada": "TERMINADO", "Resuelto en rama": "EN REVISION"}.get(d["estado"], "POR HACER")
    desc = "\n".join([
        "**Severidad:** %s · **Prioridad:** %s · **Estado:** %s" % (d["severidad"], d["prioridad"], d["estado"]),
        "**HU:** %s" % d["hu"], "**Responsable:** a asignar por el equipo", "**Encontrado:** %s" % d["encontrado"],
        "", "**Pasos:** %s" % d["pasos"], "", "**Esperado:** %s" % d["esperado"], "", "**Obtenido:** %s" % d["obtenido"],
        "", "**Causa raíz y tratamiento:** %s" % d["causa"], "", "**Evidencia:** %s" % d["evidencia"],
    ])
    tarjetas.append({"nombre": "%s [%s] %s" % (d["id"], d["severidad"], d["titulo"]), "lista": lista, "desc": desc, "etiqueta": "Defecto"})

(AQUI / "tarjetas.json").write_text(json.dumps(tarjetas, ensure_ascii=False), encoding="utf-8")
print("%d tarjetas (%d casos y %d defectos) en tarjetas.json" % (len(tarjetas), len(CASOS), len(tarjetas) - len(CASOS)))
