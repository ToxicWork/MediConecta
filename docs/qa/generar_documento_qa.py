"""Arma el documento del QA (docs/qa/documento-qa.html) con los datos de datos_qa.py, datos_defectos.py y los
resultados de la última corrida del pipeline (resultados.json). El PDF sale con docs/generar_pdf.py:

    python docs/qa/generar_documento_qa.py
    python docs/generar_pdf.py qa/documento-qa.html qa/Clase15_Grupo4_MediConecta_Testing.pdf
"""
import html, json, pathlib, re, sys
from collections import Counter

AQUI = pathlib.Path(__file__).parent
sys.path.insert(0, str(AQUI))
from datos_qa import HISTORIAS, CASOS  # noqa: E402
from datos_defectos import DEFECTOS  # noqa: E402

R = json.loads((AQUI / "resultados.json").read_text(encoding="utf-8"))
CASO = {c["id"]: c for c in CASOS}
e = html.escape


def resultado(clave):
    """Texto del resultado de un caso según su clave en resultados.json."""
    r = R["suites"].get(clave)
    if r is None:
        return "Sin ejecutar"
    if "texto" in r:
        return r["texto"]
    return ("Pasó" if not r["fallas"] else "Falló") + " (%d OK, %d fallas)" % (r["ok"], r["fallas"])


def tabla(num, titulo, columnas, filas, anchos=None, clase="chica", nota=None):
    th = "".join('<th%s>%s</th>' % (' style="width:%s"' % anchos[i] if anchos and anchos[i] else "", c) for i, c in enumerate(columnas))
    cuerpo = "\n".join("<tr>%s</tr>" % "".join("<td>%s</td>" % celda for celda in fila) for fila in filas)
    pie = '<div class="nota"><em class="etq">Nota.</em> %s</div>' % nota if nota else ""
    return ('<div class="tabla" style="page-break-inside:auto"><div class="num">Tabla %d</div><div class="tit">%s</div>'
            '<table class="%s"><thead><tr>%s</tr></thead><tbody>%s</tbody></table>%s</div>') % (num, titulo, clase, th, cuerpo, pie)


class Tablas:
    n = 0

    def __call__(self, *a, **k):
        Tablas.n += 1
        return tabla(Tablas.n, *a, **k)


T = Tablas()


def p(texto, sangria=True):
    return '<p%s>%s</p>' % ("" if sangria else ' class="sin-sangria"', texto)


def lista(items):
    return '<ul class="lista">%s</ul>' % "".join("<li>%s</li>" % i for i in items)


# ---- Cálculos ---------------------------------------------------------------------------------------------------
tipos = Counter(c["tipo"] for c in CASOS)
negativos = tipos["Negativo"] + tipos["Borde"]
pct_negativos = 100 * negativos / len(CASOS)
automatizados = sum(1 for c in CASOS if c["prueba"] != "Manual")
criterios = [(h, c) for h in HISTORIAS for c in h["criterios"]]
faltan = [c[0] for _, c in criterios for cp in c[3] if cp not in CASO]
if faltan:
    sys.exit("Criterios con casos inexistentes: %s" % faltan)
sev = Counter((d["severidad"], d["estado"]) for d in DEFECTOS)
abiertos = [d for d in DEFECTOS if d["estado"] in ("Abierto", "Limitación aceptada")]
mayores_abiertos = [d for d in abiertos if d["severidad"] == "Mayor"]
criticos_abiertos = [d for d in abiertos if d["severidad"] in ("Bloqueante", "Crítica")]


def estado_criterio(casos):
    estados = [resultado(CASO[c]["resultado"]) for c in casos]
    if any(s.startswith("Falló") for s in estados):
        return "Falló"
    if all(s.startswith("Manual") or s == "Sin ejecutar" for s in estados):
        return "Manual" if any(s.startswith("Manual") for s in estados) else "Sin ejecutar"
    if any(s.startswith("Manual") or s.startswith("Sin ejecutar") for s in estados):
        return "Pasó en parte"  # lo automático pasó; falta la parte manual o la que no se ejecutó
    return "Pasó"


estados_criterios = Counter(estado_criterio(c[3]) for _, c in criterios)
pct_criterios = 100 * estados_criterios["Pasó"] / len(criterios)

# ---- Secciones --------------------------------------------------------------------------------------------------
indice = [("intro", "Introducción", 1), ("plan", "1. Plan y estrategia de pruebas", 1), ("alcance", "Alcance y fuera de alcance", 2),
          ("niveles", "Niveles, herramientas y entornos", 2), ("datos", "Datos de prueba", 2), ("criterios-es", "Criterios de entrada y de salida", 2),
          ("riesgos", "Riesgos priorizados", 2), ("hu", "2. Historias de usuario y matriz de trazabilidad", 1),
          ("gherkin", "Historias, prioridad y criterios de aceptación", 2), ("matriz", "Matriz de trazabilidad", 2),
          ("casos", "3. Especificación de casos", 1), ("ejecucion", "4. Informe de ejecución", 1), ("defectos", "5. Registro de defectos", 1),
          ("seguridad", "6. Informe de seguridad", 1), ("ia", "7. Declaración y plan de uso de IA", 1),
          ("lecciones", "8. Lecciones aprendidas", 1), ("referencias", "Referencias", 1)]

partes = []
partes.append("""<section class="caratula">
  <div class="institucion-cabecera"><div class="sigla">UADE</div><div class="nombre-uni">UNIVERSIDAD ARGENTINA DE LA EMPRESA</div>
  <div class="facultad">Facultad de Ingeniería y Ciencias Exactas</div><div class="facultad">Departamento de Tecnología Informática</div><div class="filete"></div></div>
  <div class="titulo">Trabajo Práctico: Desarrollo de Aplicaciones II</div>
  <div class="subtitulo">MediConecta: documentación de pruebas</div>
  <div>Plan, trazabilidad, casos, ejecución, defectos, seguridad, uso de IA y lecciones aprendidas (ISO/IEC/IEEE 29119-3)</div>
  <div class="bloque"><div>Grupo 4</div><div>Agustín Bodas, Máximo Bonarrico y Sergio Casanova</div><div>QA responsable: %s</div></div>
  <div class="bloque"><div>Universidad Argentina de la Empresa</div><div>3.4.218 Desarrollo de Aplicaciones II, Comisión Lunes TT</div>
  <div>Prof. Matías Ruiz</div><div>%s</div></div>
</section>""" % (e(R["qa_responsable"]), e(R["fecha_documento"])))
partes.append('<section class="indice salto"><h1>Índice</h1><ul>%s</ul></section>' % "".join(
    '<li%s><a href="#%s">%s</a><span class="relleno"></span><span class="pag" data-para="%s"></span></li>' % (' class="n2"' if nivel == 2 else "", i, e(t), i)
    for i, t, nivel in indice))

# Introducción
enlaces = [("Repositorio", R["enlaces"]["repo"]), ("Pipeline (corrida de este informe)", R["enlaces"]["run"]),
           ("Tablero (casos y defectos)", R["enlaces"]["tablero"]), ("SonarCloud", R["enlaces"]["sonar"]),
           ("Contrato de la API (OpenAPI)", R["enlaces"]["openapi"]), ("Reportes (artefactos de la corrida)", R["enlaces"]["run"])]
partes.append('<section class="salto"><h1 id="intro">Introducción</h1>' + p(
    "Este documento reúne la documentación de pruebas de MediConecta según la estructura de ISO/IEC/IEEE 29119-3, tal como la pide "
    "la cátedra para la prueba del sistema. Cada resultado sale de una ejecución real del pipeline de integración continua "
    "(corrida <span class=\"mono\">%s</span>, %s, rama <span class=\"mono\">%s</span>) y se puede repetir con los scripts del "
    "repositorio. Lo que no se pudo verificar se dice como tal." % (e(R["corrida"]), e(R["fecha_corrida"]), e(R["rama"]))) + p(R["estado_ramas"]) +
    T("Enlaces de la entrega", ["Qué", "Dónde"], [[e(a), '<span class="mono">%s</span>' % e(b)] for a, b in enlaces], ["35%", None]) + "</section>")

# 1. Plan
niveles = [
    ["Unitario", "JUnit 5 y Mockito; JaCoCo; PIT (mutación del dominio)", "Reglas del dominio y de los servicios EJB, sin contenedor", "%d pruebas en %d clases" % (R["junit"]["pruebas"], R["junit"]["clases"])],
    ["Integración", "Arquillian (contenedor remoto) dentro del WildFly del pipeline, con su PostgreSQL y su broker Artemis; además, las suites de punta a punta", "Repositorios (JPA sobre PostgreSQL, cifrado de columnas, idempotencia) y consumidores de mensajes (los MDB de la aplicación con el evento real), SOAP y REST externos", "RepositoriosIT y ConsumidoresDeMensajesIT (src/it/java, mvn -P integracion verify), sin mocks"],
    ["Contrato", "prueba_contrato_api.py contra el OpenAPI; prueba_contrato_wsdl.py contra el WSDL del cliente", "Cada respuesta documentada de la API REST; operaciones, tipos y enumeraciones del servicio SOAP", "29 de 30 respuestas; el WSDL publicado por el legado coincide campo por campo con el contrato"],
    ["E2E", "Playwright (Python) con Chrome, en el pipeline", "Flujos completos de los tres roles, defectos y seguridad", "%d suites" % R["e2e"]["suites"]],
    ["No funcional", "k6 (carga), OWASP ZAP (seguridad), axe-core (accesibilidad), prueba responsive, gitleaks y Trivy", "Rendimiento, vulnerabilidades, WCAG 2.2 AA, diseño a 360/768/1280 px, secretos y dependencias", "Todas en el pipeline"],
    ["Manual", "Exploratoria guiada; tarea del manual de usuario", "Lo que el docente prueba sin guion", "Charters en la sección 3"],
]
entornos = [["Pipeline (evidencia de este informe)", "GitHub Actions, ubuntu-24.04: WildFly 41.0.0 (zip de Maven Central con checksum), PostgreSQL de la imagen, Google Chrome, Java 25, Python 3.12. Usuarios de demo creados por la propia aplicación; contraseñas al azar por corrida"],
            ["Desarrollo (Windows y macOS)", "WildFly 41 con standalone-full, base en Supabase (PostgreSQL), sistemas externos simulados (externos.war)"],
            ["Demostración en clase", "El entorno de desarrollo de un integrante con los datos de demo (SETUP.md)"]]
riesgos = [
    ["R-01", "Dos operaciones simultáneas sobre un turno (doble reserva, doble cancelación)", "Alta", "Alto", "CP-05, CP-20, CP-46, CP-92 (D-45)"],
    ["R-02", "Pérdida de mensajes si el broker o un suscriptor se cae", "Media", "Alto", "CP-14, CP-35, CP-23"],
    ["R-03", "El legado de la obra social caído, lento o con Fault", "Alta", "Alto", "CP-09, CP-10, CP-13, CP-72"],
    ["R-04", "La pasarela falla durante el cobro o el reintegro", "Media", "Alto", "CP-07, CP-11, CP-23 (D-06 y D-41 abiertos)"],
    ["R-05", "Acceso a datos clínicos ajenos (BOLA) o de otro rol (BFLA)", "Media", "Alto", "CP-18, CP-27, CP-36, CP-43, CP-64"],
    ["R-06", "Fuerza bruta, inyección o token robado", "Media", "Alto", "CP-47, CP-49, CP-55, CP-56, CP-58"],
    ["R-07", "Datos personales o de salud en logs y mensajes", "Baja", "Alto", "CP-54, CP-67"],
    ["R-08", "Inyección de eventos falsos en el broker", "Baja", "Alto", "CP-59"],
    ["R-09", "Pantallas lentas con volumen real (N+1)", "Media", "Medio", "CP-45, CP-79"],
    ["R-10", "Sesión o conversación vencida que rompe la pantalla", "Media", "Medio", "CP-04, CP-62 (D-43), CP-92 (D-46)"],
    ["R-11", "Pruebas inestables que esconden defectos", "Media", "Medio", "CP-83 y tres corridas idénticas"],
    ["R-12", "Base de Supabase pausada o bloqueada por la red el día de la demo", "Media", "Alto", "Lista de control previa (D-11); el pipeline no depende de Supabase"],
]
partes.append('<section class="salto"><h1 id="plan">1. Plan y estrategia de pruebas</h1><h2 id="alcance">Alcance y fuera de alcance</h2>' +
    p("Alcance: las diez historias de usuario comprometidas (HU-01 a HU-10) con sus criterios; la telemedicina y la administración "
      "(pagos, turnos, usuarios, desbloqueo); las integraciones con el legado SOAP de la obra social, la pasarela de pagos y el proveedor "
      "de videoconsultas (REST), y la mensajería (tópico de turnos y cola de autorizaciones); la API REST versionada; y los criterios de "
      "seguridad SEC del diseño.") +
    p("Fuera de alcance: Mercado Pago real (la pasarela es un simulador), una aplicación móvil (no hay), la funcionalidad de IA dentro del "
      "sistema (MediConecta no la tiene; consulta pendiente con la cátedra) y el arranque con un único comando con contenedores (G1, eximido "
      "por la cátedra el 18/11).") +
    '<h2 id="niveles">Niveles, herramientas y entornos</h2>' +
    T("Niveles de prueba", ["Nivel", "Herramientas", "Qué cubre", "Estado"], niveles, ["13%", "30%", "32%", None]) +
    T("Entornos", ["Entorno", "Composición"], entornos, ["28%", None]) +
    '<h2 id="datos">Datos de prueba</h2>' +
    p("Los usuarios de demo (Dra. Ana Lopez, profesional; Juan Perez, paciente con OSDE 210; Carla Admin, administrativa) los crea "
      "<span class=\"mono\">pruebas/sembrar_usuarios_demo.py</span> a través de la propia aplicación. Cada suite crea sus propios pacientes "
      "con emails al azar y usa horarios relativos a la fecha de la corrida, de 5 en 5 minutos y en rangos de días distintos para no chocar "
      "entre sí. La consola de los sistemas externos (<span class=\"mono\">/externos</span>) pone cada sistema en línea, caído o lento y "
      "decide la respuesta de las autorizaciones. Las tarjetas son de prueba del simulador.") +
    '<h2 id="criterios-es">Criterios de entrada y de salida</h2>' +
    lista(["Entrada: compila y pasan las pruebas unitarias; la aplicación y los sistemas externos despliegan; los usuarios de demo existen.",
           "Salida: 0 defectos bloqueantes o críticos abiertos; como máximo 3 mayores abiertos, con alternativa documentada; 100 % de las HU "
           "ejecutadas y 95 % de los criterios pasados; ningún criterio de una HU Must fallado; cobertura de 80 % de líneas y 70 % de ramas en "
           "negocio y dominio; mutación de 60 %; Quality Gate aprobado; 0 alertas High de ZAP; 0 CVE altas o críticas; pipeline de 15 minutos "
           "o menos y 0 pruebas inestables."]) +
    '<h2 id="riesgos">Riesgos priorizados</h2>' +
    T("Riesgos de producto: probabilidad, impacto y casos que los cubren", ["Id", "Riesgo", "Probabilidad", "Impacto", "Cubierto por"], riesgos,
      ["7%", "45%", "12%", "10%", None], nota="El nivel combina probabilidad e impacto (matriz de 3 × 3); los de impacto alto y probabilidad media o alta tienen al menos una prueba de falla en el pipeline.") +
    p("Rol del QA: el equipo designará al responsable antes de la entrega; hasta entonces los casos y defectos del tablero figuran sin "
      "responsable asignado. Cualquier integrante puede explicar cualquier prueba.") + "</section>")

# 2. Historias y trazabilidad
filas_hu = []
for h in HISTORIAS:
    crit = "".join("<li><b>%s</b> (%s). %s</li>" % (c[0], c[1], e(c[2])) for c in h["criterios"])
    filas_hu.append(["<b>%s</b><br>%s" % (h["id"], e(h["titulo"])), "<b>%s</b><br>%s" % (h["moscow"], e(h["porque"])),
                     e(h["historia"]) + '<ul class="lista">%s</ul>' % crit])
matriz = []
for h, c in criterios:
    casos = c[3]
    pruebas = sorted({CASO[x]["prueba"] for x in casos})
    defs = sorted({d for x in casos for d in CASO[x]["defectos"]})
    matriz.append([h["id"], c[0], ", ".join(casos), "<span class=\"mono\">%s</span>" % e("; ".join(pruebas)),
                   estado_criterio(casos), "Corrida %s" % e(R["corrida"]) if estado_criterio(casos) == "Pasó" else "-", ", ".join(defs) or "-"])
must_fallados = [c[0] for h, c in criterios if h["moscow"] == "Must" and estado_criterio(c[3]) == "Falló"]
partes.append('<section class="salto"><h1 id="hu">2. Historias de usuario y matriz de trazabilidad</h1><h2 id="gherkin">Historias, prioridad y criterios de aceptación</h2>' +
    p("Cada historia tiene al menos tres criterios en formato Dado / Cuando / Entonces (camino feliz, alternativo y de error) y las que "
      "integran sistemas externos suman uno ante su falla. La prioridad es MoSCoW: Must las del flujo principal (reservar, confirmar, "
      "cancelar y publicar), Should las que agregan valor sin bloquearlo y Could el Monitor de eventos. Won't (en esta entrega): Mercado "
      "Pago real y la aplicación móvil.") +
    T("Historias de usuario, prioridad MoSCoW y criterios de aceptación", ["Historia", "Prioridad", "Historia y criterios"], filas_hu, ["17%", "17%", None]) +
    '<h2 id="matriz">Matriz de trazabilidad</h2>' +
    p("HU → criterio → caso → test automatizado → resultado → evidencia → defecto. Ningún criterio queda sin caso. Resultado del pipeline: "
      "%d de %d criterios pasados (%.0f %%), %d pasados en parte (falta su verificación manual), %d manuales y %d fallados; criterios Must fallados: %s." % (
          estados_criterios["Pasó"], len(criterios), pct_criterios, estados_criterios["Pasó en parte"], estados_criterios["Manual"], estados_criterios["Falló"], ", ".join(must_fallados) or "ninguno")) +
    T("Matriz de trazabilidad", ["HU", "Criterio", "Casos", "Test automatizado", "Resultado", "Evidencia", "Defectos"], matriz,
      ["7%", "8%", "11%", "34%", "9%", "15%", None]) + "</section>")

# 3. Casos
filas_casos = [["<b>%s</b>" % c["id"], "<b>%s</b><br>%s" % (e(c["titulo"]), ", ".join(c["hu"])), e(c["pre"]), e(c["datos"]), e(c["pasos"]), e(c["esperado"]),
                "%s<br>%s<br>%s" % (c["tipo"], c["prioridad"], c["nivel"]), "Sí: <span class=\"mono\">%s</span>" % e(c["prueba"]) if c["prueba"] != "Manual" else "No"] for c in CASOS]
charters = [["Datos inválidos", "Fechas, importes, emails y tarjetas fuera de formato en cada formulario", "Ningún error 500; mensajes que dicen qué corregir"],
            ["Doble clic y volver atrás", "Reservar, pagar, publicar y cancelar con doble clic; volver atrás después de confirmar", "Una sola operación; sin estados a medias"],
            ["Sesión vencida", "Dejar la Agenda quieta 6 minutos y más de 30; seguir operando", "La pantalla se recupera o pide ingresar de nuevo, sin error (D-43)"],
            ["Recarga a mitad de operación", "Recargar en el pago, en la sala y en la historia", "Ningún cobro doble ni turno en estado inconsistente"],
            ["Dos usuarios a la vez", "Paciente y profesional sobre el mismo turno; dos pacientes sobre el mismo horario", "Uno gana y el otro recibe un mensaje claro (D-36)"]]
partes.append('<section class="salto"><h1 id="casos">3. Especificación de casos</h1>' +
    p("%d casos: %d positivos, %d negativos y %d de borde. Los negativos y de borde son el %.0f %% (mínimo pedido: 40 %%). %d están "
      "automatizados y corren en el pipeline; %d son manuales. Cada caso agrupa las verificaciones de un escenario de la suite indicada; "
      "los identificadores son estables y se usan en el tablero." % (len(CASOS), tipos["Positivo"], tipos["Negativo"], tipos["Borde"], pct_negativos,
                                                                   automatizados, len(CASOS) - automatizados)) +
    T("Especificación de casos de prueba", ["Id", "Caso y HU", "Precondición", "Datos", "Pasos", "Resultado esperado", "Tipo, prioridad y nivel", "Automatizado"],
      filas_casos, ["5%", "13%", "13%", "11%", "15%", "21%", "9%", None]) +
    T("Charters de la prueba exploratoria (CP-92)", ["Cargo", "Qué se explora", "Qué se espera", "Resultado (19/11)"],
      [c + [e(R.get("charters_resultado", {}).get(c[0], "Sin ejecutar"))] for c in charters], ["15%", "30%", "25%", None]) + "</section>")

# 4. Ejecución
niveles_ej = [[n["nivel"], str(n["ejecutados"]), str(n["pasados"]), str(n["fallados"]), str(n["bloqueados"]), e(n["detalle"])] for n in R["niveles"]]
suites = [["<span class=\"mono\">%s</span>" % k, str(v["ok"]), str(v["fallas"]), v.get("tiempo", "-")] for k, v in R["suites"].items() if "ok" in v]
metricas = [[e(m[0]), e(m[1]), e(m[2]), m[3]] for m in R["metricas"]]
partes.append('<section class="salto"><h1 id="ejecucion">4. Informe de ejecución</h1>' +
    p("Corrida %s del %s sobre la rama %s, en %s. Los totales por nivel y por suite salen de los resúmenes del pipeline; la cobertura, "
      "de JaCoCo; la mutación, de PIT; el análisis estático, de SonarCloud." % (e(R["corrida"]), e(R["fecha_corrida"]), e(R["rama"]), e(R["duracion_pipeline"]))) +
    T("Totales por nivel", ["Nivel", "Ejecutados", "Pasados", "Fallados", "Bloqueados", "Detalle"], niveles_ej, ["15%", "11%", "10%", "10%", "11%", None]) +
    T("Métricas y umbrales", ["Métrica", "Valor", "Umbral", "Cumple"], metricas, ["38%", "28%", "20%", None]) +
    T("Suites de punta a punta: verificaciones por suite", ["Suite", "OK", "Fallas", "Tiempo"], suites, ["45%", "15%", "15%", None]) +
    T("Prueba de carga con k6 (CP-79): 50 usuarios durante 5 minutos", ["Endpoint", "Pedidos", "Mediana", "p95", "p99", "Errores"],
      [[e(f[0]), f[1], f[2], f[3], f[4], f[5]] for f in R["carga"]["filas"]], ["40%", None, None, None, None, None], nota=e(R["carga"]["nota"])) +
    ('<div class="figura"><div class="num">Figura 1</div><div class="tit">Quality Gate de SonarCloud</div><img src="%s" alt="Quality Gate de SonarCloud"></div>' % R["captura_sonar"]
     if R.get("captura_sonar") else "") +
    (T("Pruebas manuales (CP-90 a CP-92), ejecutadas con asistencia de IA y pendientes de la revisión de un integrante",
       ["Caso", "Qué", "Cómo se ejecutó", "Resultado", "Evidencia"],
       [["<b>%s</b>" % m[0], e(m[1]), e(m[2]), e(m[3]), '<span class="mono">%s</span>' % e(m[4])] for m in R["manuales"]],
       ["7%", "13%", "30%", "34%", None]) +
     '<div class="figura"><div class="num">Figura 2</div><div class="tit">CP-90: el aviso del paciente en la bandeja de la '
     'profesional, sin recargar la página (6,9 s después de confirmar)</div><img src="img/manual/cp90-2-aviso-sin-recargar.jpg" '
     'alt="Bandeja de notificaciones de la Dra. Ana Lopez con el aviso nuevo arriba"></div>'
     if R.get("manuales") else "") + "</section>")

# 5. Defectos
filas_def = [["<b>%s</b>" % d["id"], e(d["titulo"]), "%s<br>%s" % (d["severidad"], d["prioridad"]), e(d["hu"]), e(d["pasos"]),
              "<b>Esperado:</b> %s<br><b>Obtenido:</b> %s" % (e(d["esperado"]), e(d["obtenido"])), e(d["evidencia"]),
              "<b>%s</b><br>%s" % (d["estado"], e(d["causa"]))] for d in DEFECTOS]
resumen = Counter(d["estado"] for d in DEFECTOS)
partes.append('<section class="salto"><h1 id="defectos">5. Registro de defectos</h1>' +
    p("%d defectos registrados: %s. Abiertos o aceptados con alternativa: %d, de los cuales %d mayores (%s; máximo admitido: 3 con "
      "alternativa documentada) y %d bloqueantes o críticos. Severidades según la sección 9 de los criterios. Los D-13 a D-43 tienen tarjeta "
      "en el tablero; los D-01 a D-12, del informe del 14/11 y del 16/11, también se cargaron. En el informe del 16/11, D-13 y D-14 "
      "designaban otros dos pendientes, que acá pasan a D-41 y D-42 para no repetir identificadores." % (
          len(DEFECTOS), ", ".join("%d %s" % (n, k.lower()) for k, n in resumen.items()), len(abiertos), len(mayores_abiertos),
          ", ".join(d["id"] for d in mayores_abiertos), len(criticos_abiertos))) +
    T("Registro de defectos", ["Id", "Defecto", "Severidad y prioridad", "HU", "Pasos para reproducir", "Esperado y obtenido", "Evidencia", "Estado y causa raíz"],
      filas_def, ["5%", "14%", "8%", "8%", "15%", "21%", "12%", None]) + "</section>")

# 6. Seguridad
sec = [[e(s[0]), e(s[1]), "<b>%s</b>" % s[2], e(s[3])] for s in R["seguridad"]]
zap = [[e(z[0]), e(z[1]), str(z[2]), e(z[3])] for z in R["zap"]["alertas"]]
partes.append('<section class="salto"><h1 id="seguridad">6. Informe de seguridad</h1>' +
    p("Base: los criterios SEC del diseño (Clase 11: OWASP ASVS, API Security Top 10 y Ley 25.326) y las pruebas en vivo del eje D. Un "
      "criterio figura CUMPLE sólo con una prueba ejecutada y su evidencia.") +
    T("Matriz de criterios de seguridad", ["Criterio", "Cómo se verifica", "Estado", "Evidencia"], sec, ["27%", "33%", "12%", None]) +
    p(e(R["zap"]["resumen"])) +
    (T("Alertas de OWASP ZAP y su tratamiento", ["Riesgo", "Alerta", "Instancias", "Tratamiento"], zap, ["10%", "30%", "10%", None]) if zap else "") +
    "<h2>Hallazgos y su tratamiento</h2>" + lista([e(x) for x in R["hallazgos"]]) + "</section>")

# 7. IA
partes.append('<section class="salto"><h1 id="ia">7. Declaración y plan de uso de IA</h1>' +
    T("Herramientas de IA usadas en las pruebas", ["Herramienta y versión", "Para qué se usó", "Pedido representativo", "Qué se aceptó", "Qué se corrigió o descartó", "Cómo se verificó"],
      [[e(x) for x in fila] for fila in R["ia"]["herramientas"]], ["13%", "17%", "18%", "17%", "18%", None]) +
    "<h2>Casos en que la IA se equivocó</h2>" + lista([e(x) for x in R["ia"]["errores"]]) +
    p(e(R["ia"]["firma"])) +
    "<h2>Plan de IA por usar</h2>" + T("Plan de IA: objetivo medible y riesgo que controla", ["Uso", "Objetivo medible", "Riesgo que controla"],
                                         [[e(x) for x in fila] for fila in R["ia"]["plan"]], ["30%", "35%", None]) +
    p(e(R["ia"]["funcionalidad"])) + "</section>")

# 8. Lecciones
partes.append('<section class="salto"><h1 id="lecciones">8. Lecciones aprendidas</h1>' +
    T("Tres defectos que llegaron tarde", ["Defecto", "Por qué no se detectó antes", "Qué prueba lo habría atrapado (y ahora lo atrapa)"],
      [[e(x) for x in fila] for fila in R["lecciones"]], ["22%", "39%", None]) +
    '<h1 id="referencias">Referencias</h1><div class="referencias">' + "".join(p(e(r)) for r in R["referencias"]) + "</div></section>")

estilo = (AQUI / "estilo-apa.css").read_text(encoding="utf-8")
documento = ('<!DOCTYPE html>\n<html lang="es-AR">\n<head>\n<meta charset="UTF-8">\n<title>MediConecta: documentación de pruebas (Grupo 4)</title>\n'
             '<meta name="author" content="Agustín Bodas, Máximo Bonarrico y Sergio Casanova">\n<style>\n%s\n'
             'table.chica td, table.chica th { font-size: 8pt; line-height: 1.25; vertical-align: top; }\n'
             'table.chica ul.lista li { line-height: 1.3; }\n</style>\n</head>\n<body>\n%s\n</body>\n</html>\n') % (estilo, "\n".join(partes))
(AQUI / "documento-qa.html").write_text(documento, encoding="utf-8")
print("documento-qa.html: %d casos (%.0f %% negativos o de borde), %d criterios (%.0f %% pasados), %d defectos (%d mayores abiertos), %d tablas"
      % (len(CASOS), pct_negativos, len(criterios), pct_criterios, len(DEFECTOS), len(mayores_abiertos), Tablas.n))
