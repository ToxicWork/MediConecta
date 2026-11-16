"""Genera MediConecta_Presentacion_16-11.pptx (python-pptx).

Uso:  python docs/entrega-nucleo/generar_presentacion.py
El contenido está en este archivo: para cambiar una diapositiva se edita acá y se vuelve a generar.
"""
import os
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt

AZUL, ACENTO, TEXTO, TENUE, NARANJA = "16308C", "2F5FE0", "1B2330", "55607A", "B3541E"
GRIS, CELESTE, ROJO, VERDE, BLANCO = "F6F7FA", "EEF3FF", "C0392B", "1E8E5A", "FFFFFF"
QUIEN = {"Máximo": "1E8E5A", "Sergio": "1F6FD6", "Agustín": "24324F", "Los tres": "777777"}
PIE = "MediConecta · Grupo 4 · Entrega del 16/11"

prs = Presentation()
prs.slide_width, prs.slide_height = Inches(13.333), Inches(7.5)


def rgb(h):
    return RGBColor.from_string(h)


def caja(s, x, y, w, h, color, forma=MSO_SHAPE.ROUNDED_RECTANGLE, borde=None):
    f = s.shapes.add_shape(forma, Inches(x), Inches(y), Inches(w), Inches(h))
    f.fill.solid()
    f.fill.fore_color.rgb = rgb(color)
    if borde:
        f.line.color.rgb = rgb(borde)
        f.line.width = Pt(1)
    else:
        f.line.fill.background()
    f.shadow.inherit = False
    if forma == MSO_SHAPE.ROUNDED_RECTANGLE:
        f.adjustments[0] = min(0.12, 0.09 / max(min(w, h), 0.1))
    return f


def texto(s, x, y, w, h, lineas, tam=16, color=TEXTO, negrita=False, alin=PP_ALIGN.LEFT, fuente="Segoe UI",
          centro_v=False, espacio=0):
    """lineas: str o lista de str / (str, {tam, color, negrita, fuente})."""
    t = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    tf = t.text_frame
    tf.word_wrap = True
    tf.margin_left = tf.margin_right = Inches(0.05)
    tf.margin_top = tf.margin_bottom = Inches(0.03)
    if centro_v:
        tf.vertical_anchor = MSO_ANCHOR.MIDDLE
    if isinstance(lineas, str):
        lineas = [lineas]
    for i, linea in enumerate(lineas):
        tramos = linea if isinstance(linea, list) else [linea]
        p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        p.alignment = alin
        if espacio:
            p.space_after = Pt(espacio)
        for tramo in tramos:
            op = {}
            if isinstance(tramo, tuple):
                tramo, op = tramo
            r = p.add_run()
            r.text = tramo
            r.font.name = op.get("fuente", fuente)
            r.font.size = Pt(op.get("tam", tam))
            r.font.bold = op.get("negrita", negrita)
            r.font.color.rgb = rgb(op.get("color", color))
    return t


def guia(palabra, explicacion, color=None):
    """Una línea con la palabra guía en negrita y la explicación al lado."""
    op = {"negrita": True}
    if color:
        op["color"] = color
    return [(palabra + "  ", op), explicacion]


def pastilla(s, x, y, w, etiqueta, color, tam=12, h=0.34):
    f = caja(s, x, y, w, h, color)
    f.adjustments[0] = 0.5
    tf = f.text_frame
    tf.margin_top = tf.margin_bottom = 0
    p = tf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    r = p.add_run()
    r.text = etiqueta
    r.font.name, r.font.size, r.font.bold = "Segoe UI", Pt(tam), True
    r.font.color.rgb = rgb(BLANCO)
    return f


def codigo(s, x, y, w, h, lineas, tam=12, titulo=None):
    if titulo:
        texto(s, x, y, w, 0.32, titulo, tam=12, color=TENUE, negrita=True, fuente="Consolas")
        y, h = y + 0.32, h - 0.32
    caja(s, x, y, w, h, "1B2330")
    filas = []
    for linea in lineas:
        comentario = linea.strip().startswith("//") or linea.strip().startswith("#") or linea.strip().startswith("<!--")
        filas.append((linea if linea else " ", {"color": "8FD19E" if comentario else "E6ECF7"}))
    texto(s, x + 0.15, y + 0.1, w - 0.3, h - 0.2, filas, tam=tam, fuente="Consolas")


def tarjeta(s, x, y, w, h, titulo, lineas, color=GRIS, tam=13.5, color_titulo=ACENTO, tam_titulo=16):
    caja(s, x, y, w, h, color)
    texto(s, x + 0.18, y + 0.1, w - 0.36, 0.4, titulo, tam=tam_titulo, negrita=True, color=color_titulo)
    texto(s, x + 0.18, y + 0.55, w - 0.36, h - 0.65, lineas, tam=tam, espacio=4)


def diapositiva(titulo, quienes=(), tiempo=None, en_vivo=False):
    s = prs.slides.add_slide(prs.slide_layouts[6])
    caja(s, 0, 0, 13.333, 1.0, AZUL, MSO_SHAPE.RECTANGLE)
    texto(s, 0.5, 0.2, 9.8, 0.6, titulo, tam=27, negrita=True, color=BLANCO)
    x = 12.73
    for q in reversed(quienes):
        x -= 1.25
        pastilla(s, x, 0.33, 1.15, q, QUIEN[q])
        x -= 0.1
    if en_vivo:
        pastilla(s, 11.2, 6.75, 1.7, "●  EN VIVO", ROJO, tam=13, h=0.42)
    n = len(prs.slides)
    texto(s, 0.5, 6.95, 6, 0.3, "%s · %d" % (PIE, n), tam=10, color=TENUE)
    return s


def tabla(s, x, y, anchos, filas, alto=0.5, tam=12.5, alto_cab=0.42):
    """Primera fila = encabezado. Cada celda: str o (str, opciones de texto)."""
    cy = y
    for i, fila in enumerate(filas):
        h = alto_cab if i == 0 else alto
        cx = x
        caja(s, x, cy, sum(anchos), h - 0.05, AZUL if i == 0 else (GRIS if i % 2 else CELESTE))
        for ancho, celda in zip(anchos, fila):
            op = {}
            if isinstance(celda, tuple):
                celda, op = celda
            texto(s, cx + 0.08, cy, ancho - 0.16, h - 0.05, [(celda, op)], tam=tam,
                  color=BLANCO if i == 0 else TEXTO, negrita=(i == 0), centro_v=True)
            cx += ancho
        cy += h


# 1 · Portada ---------------------------------------------------------------------------------------
s = prs.slides.add_slide(prs.slide_layouts[6])
caja(s, 0, 0, 13.333, 7.5, AZUL, MSO_SHAPE.RECTANGLE)
texto(s, 0.9, 1.2, 11.5, 1.2, "MediConecta", tam=60, negrita=True, color=BLANCO)
texto(s, 0.9, 2.4, 11.5, 0.7, "Turnos médicos, de la reserva al cobro", tam=28, color="DCE6FF")
texto(s, 0.9, 3.4, 11.5, 1.5, [
    "Qué construimos, cómo funciona y qué nos falta.",
    "Lo mostramos andando y abrimos el código de cada parte."], tam=20, color=BLANCO, espacio=6)
texto(s, 0.9, 5.4, 11.5, 1.2, [
    ("Grupo 4 — Agustín Bodas, Máximo Bonarrico y Sergio Casanova", {"negrita": True}),
    "Desarrollo de Aplicaciones II · Comisión Lunes TT · Prof. Matías Ruiz",
    "16 de noviembre de 2026"], tam=15, color="DCE6FF", espacio=3)

# 2 · Qué vamos a mostrar ----------------------------------------------------------------------------
s = diapositiva("Qué vamos a mostrar")
texto(s, 0.5, 1.12, 12.3, 0.4,
      "En cada parte: para qué sirve, cómo funciona en vivo y el código que lo hace funcionar.",
      tam=15, color=TENUE)
AGENDA = [
    ("Cómo está armado", "Las tres capas y quién hizo cada una", "Máximo"),
    ("Un turno, de punta a punta", "Reservar, pagar y confirmar, en vivo", "Sergio"),
    ("Cómo hablamos con otros sistemas", "Obra social, pagos y video: SOAP, REST, cola y tópico", "Agustín"),
    ("Qué pasa si algo falla", "Hacemos fallar una confirmación después de cobrar", "Agustín"),
    ("Seguridad", "Ingreso con token (JWT) y permisos por rol", "Sergio"),
    ("Las reglas del turno", "Qué se puede hacer en cada estado y el error 409", "Agustín"),
    ("Los datos y las decisiones", "La base, cómo la protegemos y por qué elegimos cada cosa", "Máximo"),
    ("Las pruebas", "Cómo sabemos que anda", "Los tres"),
    ("Dónde estamos", "Qué está hecho y qué falta", "Máximo"),
]
y = 1.62
for i, (tema, detalle, quien) in enumerate(AGENDA, 1):
    caja(s, 0.5, y, 12.3, 0.5, CELESTE if i % 2 else GRIS)
    texto(s, 0.7, y + 0.03, 0.5, 0.44, str(i), tam=18, negrita=True, color=ACENTO, centro_v=True)
    texto(s, 1.3, y + 0.03, 4.3, 0.44, tema, tam=16, negrita=True, centro_v=True)
    texto(s, 5.6, y + 0.03, 5.6, 0.44, detalle, tam=13.5, color=TENUE, centro_v=True)
    pastilla(s, 11.35, y + 0.09, 1.25, quien, QUIEN[quien])
    y += 0.56

# 3 · Qué es y qué hicimos ---------------------------------------------------------------------------
s = diapositiva("Qué es MediConecta y qué hicimos", ["Máximo"])
caja(s, 0.5, 1.25, 12.3, 1.05, AZUL)
texto(s, 0.75, 1.25, 11.8, 1.05, [
    ("Una plataforma de turnos médicos.", {"negrita": True, "tam": 18}),
    "El paciente reserva, el sistema le consulta a la obra social cuánto cubre, cobra la diferencia y avisa a todos."],
      tam=14.5, color=BLANCO, centro_v=True)
HECHO = [
    ("10", "componentes", "Usuarios, turnos, historia clínica, obras sociales, pagos, telemedicina, notificaciones, facturación, agenda y monitoreo."),
    ("3", "sistemas externos", "La obra social (SOAP), la pasarela de pagos y la videoconsulta (REST). Conectados de verdad, por la red."),
    ("2", "procesos de fondo", "Una cola para las autorizaciones y un tópico para los avisos. El paciente no espera."),
    ("1", "operación todo o nada", "Confirmar un turno: si algo falla a mitad, se deshace todo y se devuelve la plata."),
]
for i, (numero, que, detalle) in enumerate(HECHO):
    x = 0.5 + i * 3.13
    caja(s, x, 2.5, 2.93, 2.5, CELESTE if i % 2 == 0 else GRIS)
    texto(s, x + 0.2, 2.55, 2.5, 0.8, numero, tam=38, negrita=True, color=ACENTO)
    texto(s, x + 0.2, 3.35, 2.6, 0.4, que, tam=14.5, negrita=True)
    texto(s, x + 0.2, 3.78, 2.6, 1.2, detalle, tam=11.5, color=TENUE)
tarjeta(s, 0.5, 5.2, 12.3, 1.5, "Y además", [
    guia("Seguridad:", "ingreso con token, permisos por rol, HTTPS y bloqueo de la cuenta tras cinco intentos fallidos."),
    guia("Calidad:", "70 pruebas unitarias y pruebas con navegador real de todo el recorrido."),
    guia("Decisiones escritas:", "18 registros que explican por qué elegimos cada solución."),
], tam=13, tam_titulo=14.5)

# 4 · Capas ------------------------------------------------------------------------------------------
s = diapositiva("Cómo está armado: tres capas", ["Máximo"])
CAPAS = [
    ("Presentación", "Sergio", "Lo que ve el usuario", [
        guia("Pantallas web", "(JSF)"),
        guia("API REST", "para otros sistemas"),
        guia("Sin reglas:", "sólo muestra y pide"),
    ]),
    ("Negocio", "Agustín", "Donde se decide", [
        guia("Servicios", "(EJB)"),
        guia("Reglas", "del turno y del cobro"),
        guia("Permisos", "por rol"),
        guia("Integraciones", "y mensajes"),
    ]),
    ("Datos", "Máximo", "Donde se guarda", [
        guia("12 DAO:", "las consultas"),
        guia("13 tablas", "en PostgreSQL"),
        guia("Supabase:", "la base en la nube"),
    ]),
]
for i, (capa, quien, lema, lineas) in enumerate(CAPAS):
    x = 0.5 + i * 4.18
    caja(s, x, 1.25, 3.95, 3.75, CELESTE if i == 2 else GRIS)
    texto(s, x + 0.25, 1.35, 2.4, 0.6, capa, tam=25, negrita=True, color=ACENTO)
    pastilla(s, x + 2.5, 1.48, 1.2, quien, QUIEN[quien])
    texto(s, x + 0.25, 1.95, 3.5, 0.4, lema, tam=14, color=TENUE)
    texto(s, x + 0.25, 2.55, 3.5, 2.4, lineas, tam=17, espacio=9)
    if i < 2:
        texto(s, x + 3.93, 2.8, 0.27, 0.5, "→", tam=22, negrita=True, color=ACENTO, alin=PP_ALIGN.CENTER)
caja(s, 0.5, 5.2, 12.3, 0.65, AZUL)
texto(s, 0.5, 5.2, 12.3, 0.65, "La regla: cada capa sólo le habla a la de abajo.", tam=19, negrita=True, color=BLANCO,
      alin=PP_ALIGN.CENTER, centro_v=True)
texto(s, 0.5, 6.0, 12.3, 0.8, [
    guia("Ejemplo:", "la pantalla de turnos no consulta la base. Se lo pide a un servicio, y el servicio a un DAO."),
    guia("Para qué:", "si cambia la base o una consulta, las pantallas y las reglas no se tocan."),
], tam=13.5, espacio=3)

# 4 · Flujo de punta a punta ------------------------------------------------------------------------
s = diapositiva("Un clic del paciente, de punta a punta", ["Sergio", "Agustín"], "1:30 – 5:30", en_vivo=True)
PASOS = [("Reserva", "hold de 5 min", GRIS), ("Cobertura", "SOAP · sincrónico", CELESTE),
         ("Cobro", "REST · sincrónico", CELESTE), ("Confirmación", "State: HOLD → CONFIRMADO", GRIS),
         ("Autorización", "cola · asincrónico", "FFF3E6"), ("Avisos", "tópico · asincrónico", "FFF3E6")]
for i, (paso, detalle, color) in enumerate(PASOS):
    x = 0.5 + i * 2.08
    caja(s, x, 1.35, 1.85, 1.15, color)
    texto(s, x, 1.42, 1.85, 0.5, paso, tam=16, negrita=True, alin=PP_ALIGN.CENTER)
    texto(s, x, 1.9, 1.85, 0.5, detalle, tam=11.5, color=TENUE, alin=PP_ALIGN.CENTER)
    if i < 5:
        texto(s, x + 1.83, 1.6, 0.27, 0.5, "→", tam=20, negrita=True, color=ACENTO, alin=PP_ALIGN.CENTER)
caja(s, 0.5, 2.65, 8.1, 0.4, AZUL)
texto(s, 0.5, 2.65, 8.1, 0.4, "Una sola transacción: si un paso falla, se deshace todo (la base y los mensajes)",
      tam=12.5, negrita=True, color=BLANCO, alin=PP_ALIGN.CENTER, centro_v=True)
caja(s, 8.82, 2.65, 3.93, 0.4, NARANJA)
texto(s, 8.82, 2.65, 3.93, 0.4, "Después, sin que el paciente espere", tam=11, negrita=True,
      color=BLANCO, alin=PP_ALIGN.CENTER, centro_v=True)
tarjeta(s, 0.5, 3.3, 6.0, 3.25, "Qué se ve en vivo", [
    "1. Reservar: la cotización dice «consultada por SOAP», $32.000, a cargo $9.600.",
    "2. Consola de externos: la llamada validarCobertura que recibió el legado.",
    "3. Tarjeta 4000…0002: pago rechazado, el turno sigue en espera.",
    "4. Tarjeta 4111…1111: turno confirmado.",
    "5. «Autorización en trámite» → «Autorizada» (la cola) y los avisos (el tópico).",
    "6. Monitor de eventos: qué suscriptor procesó cada aviso y en cuánto."], tam=13)
codigo(s, 6.75, 3.3, 6.05, 3.25, [
    "@TransactionAttribute(REQUIRED)",
    "public Turno confirmar(...) {",
    "  validaciones.paraConfirmar().validar(...); // 1",
    "  obrasSociales.validarCobertura(...);       // 2 SOAP",
    "  copagos.para(cobertura).calcular(...);     // 3",
    "  pagos.cobrar(turno, importe, datosPago);   // 4 REST",
    "  turno.confirmar(...);                      // 5 State",
    "  colaAutorizaciones.enviar(...);            // 6 cola",
    "  publicador.publicar(TURNO_CONFIRMADO);     // 7 tópico",
    "}"], tam=11.5, titulo="negocio/ProcesoConfirmacionTurno.java · confirmar (línea 103)")

# 5 · SOAP ------------------------------------------------------------------------------------------
s = diapositiva("La obra social: un sistema viejo, por SOAP", ["Agustín"], "5:30 – 9:30", en_vivo=True)
tarjeta(s, 0.5, 1.25, 6.1, 1.75, "Por qué SOAP y por qué sincrónico", [
    "La obra social es un sistema legado: sólo expone SOAP. El protocolo lo impone el tercero.",
    "Sincrónico porque el paciente necesita saber cuánto paga antes de confirmar."], color=CELESTE, tam=13.5)
tarjeta(s, 0.5, 3.12, 6.1, 1.85, "El contrato: CoberturaService (WSDL)", [
    "validarCobertura: plan, afiliado, prestación → estado y copago.",
    "solicitarAutorizacion: id de solicitud → autorizada con código o rechazada.",
    "Publicado en …/CoberturaService?wsdl; copia en el repositorio."], tam=12.5)
tarjeta(s, 0.5, 5.1, 6.1, 1.5, "Cómo lo consumimos", [
    "wsimport genera el cliente desde el WSDL en cada build.",
    "Adapter + Gateway: el negocio sólo ve CoberturaGateway.",
    "WS-Security UsernameToken con handlers JAX-WS en los dos extremos."], tam=12.5)
codigo(s, 6.85, 1.25, 5.95, 2.6, [
    "// El negocio no conoce SOAP",
    "public interface CoberturaGateway {",
    "  ResultadoCobertura validarCobertura(...);",
    "  ResultadoAutorizacion autorizar(...);",
    "}",
    "",
    "// El único que conoce el WSDL y el puerto JAX-WS",
    "class CoberturaSoapAdapter implements CoberturaGateway"],
       tam=11.5, titulo="integracion/obrasocial/")
tarjeta(s, 6.85, 4.0, 5.95, 2.6, "Si el legado se cae, la plataforma sigue", [
    "Timeout: 2 s para conectar, 3 s para responder.",
    "Un reintento a los 500 ms (la consulta no tiene efectos).",
    "Circuit Breaker: 3 fallas seguidas → 30 s sin llamar.",
    "Fallback: última validación (24 h) o cobertura pendiente.",
    "La autorización no se pierde: queda en la cola hasta que vuelve."], color="FFF3E6", tam=12.5, color_titulo=NARANJA)

# 6 · REST ------------------------------------------------------------------------------------------
s = diapositiva("Pagos, video y nuestra API: por REST", ["Agustín", "Sergio"], "5:30 – 9:30")
REST = [
    ("Pasarela de pagos", "MediConecta es cliente", [
        "POST /pasarela/v1/pagos",
        "Sincrónico: sin cobro aprobado no se confirma.",
        "Idempotency-Key: un reintento no cobra dos veces.",
        "Sin fallback: confirmar sin cobrar es regalar la consulta.",
        "Un pago rechazado es una respuesta, no una falla."]),
    ("Videoconsulta", "MediConecta es cliente", [
        "POST /video/v1/salas",
        "Sincrónico: el profesional necesita el enlace para entrar.",
        "Autenticación con API key.",
        "Su propio Circuit Breaker.",
        "Detrás de VideoconsultaGateway."]),
    ("API /api/v1", "MediConecta es servidor", [
        "Consumo externo: un portal de salud o la app de una obra social.",
        "Cartilla y disponibilidad públicas.",
        "Turnos y cancelación con JWT.",
        "Devuelve DTO, nunca entidades.",
        "Errores en JSON uniforme, sin trazas."]),
]
for i, (nombre, rol, lineas) in enumerate(REST):
    x = 0.5 + i * 4.18
    caja(s, x, 1.25, 3.95, 4.3, CELESTE if i == 2 else GRIS)
    texto(s, x + 0.2, 1.35, 3.6, 0.45, nombre, tam=19, negrita=True, color=ACENTO)
    pastilla(s, x + 0.2, 1.9, 2.4, rol, VERDE if i == 2 else "55607A", tam=11)
    texto(s, x + 0.2, 2.4, 3.6, 3.1, [(lineas[0], {"fuente": "Consolas", "tam": 12.5, "negrita": True})] + lineas[1:],
          tam=13, espacio=6)
caja(s, 0.5, 5.75, 12.3, 0.85, AZUL)
texto(s, 0.7, 5.75, 11.9, 0.85, [
    ("Por qué REST: son servicios modernos con API JSON sobre HTTP; no hay contrato rígido que heredar.", {"negrita": True}),
    "Cliente estándar de JAX-RS con JSON-B; cada partner detrás de su Gateway, igual que el SOAP."],
      tam=13, color=BLANCO, centro_v=True)

# 7 · Clasificación ---------------------------------------------------------------------------------
s = diapositiva("¿Esperar la respuesta o seguir? Cada integración", ["Agustín"], "5:30 – 9:30")
texto(s, 0.5, 1.08, 12.3, 0.6,
      "La regla: si necesito la respuesta para seguir, espero (sincrónico). Si puede esperar o les interesa a varios, "
      "dejo un mensaje y sigo (asincrónico).", tam=13, color=TENUE)
tabla(s, 0.5, 1.72, [2.9, 2.5, 6.9], [
    ["Integración", "Cómo", "Por qué"],
    ["Validar cobertura", ("SOAP · sincrónico", {"negrita": True}), "El paciente necesita el precio antes de confirmar; el legado sólo habla SOAP."],
    ["Cobrar el copago", ("REST · sincrónico", {"negrita": True}), "Sin cobro aprobado no hay turno confirmado."],
    ["Sala de videoconsulta", ("REST · sincrónico", {"negrita": True}), "El enlace se necesita en el momento de entrar."],
    ["API /api/v1", ("REST · sincrónico", {"negrita": True}), "La consumen partners externos: pedido y respuesta."],
    ["Autorizar la prestación", ("Cola punto a punto", {"negrita": True, "color": NARANJA}),
     "La procesa uno solo, una sola vez; el legado puede tardar o estar caído y nadie espera."],
    ["Eventos del turno", ("Tópico pub/sub", {"negrita": True, "color": NARANJA}),
     "Varios interesados que no se conocen: notificaciones, auditoría y facturación."],
], alto=0.5, tam=12.5)
tarjeta(s, 0.5, 5.3, 6.05, 1.35, "cola.autorizaciones", [
    "AutorizacionConsumer, dos consumidores en competencia.",
    "8 reintentos con espera creciente (10 a 60 s) y después DLQ."], color="FFF3E6", tam=12.5, color_titulo=NARANJA,
        tam_titulo=14)
tarjeta(s, 6.75, 5.3, 6.05, 1.35, "topico.turnos", [
    "Tres suscriptores durables e idempotentes.",
    "Facturación filtra con un selector: sólo TURNO_AUTORIZADO y TURNO_CANCELADO."], color="FFF3E6", tam=12.5,
        color_titulo=NARANJA, tam_titulo=14)

# 8 · Transacción y falla ---------------------------------------------------------------------------
s = diapositiva("Si algo falla a mitad de una confirmación", ["Agustín", "Sergio"], "9:30 – 12:30", en_vivo=True)
texto(s, 0.5, 1.1, 12.3, 0.4,
      "Confirmar un turno es todo o nada (una transacción). La hacemos fallar al final, cuando ya se cobró, y miramos qué pasa con cada cosa.", tam=13.5, color=TENUE)
tabla(s, 0.5, 1.7, [3.6, 5.0], [
    ["Qué tocó la confirmación", "Qué pasa cuando falla"],
    ["Base de datos (turno, pago, solicitud)", "Vuelve sola a como estaba: es parte de la transacción."],
    ["Mensajes a la cola y al tópico", "No salen nunca: el envío también es parte de la transacción."],
    ["Cobro en la pasarela (REST)", ("Ya se cobró y es otro sistema: lo devolvemos con un reembolso.", {"negrita": True})],
    ["Consulta de cobertura (SOAP)", "Fue sólo una consulta: no hay nada que deshacer."],
    ["Bitácora de integraciones", "Queda escrita igual, para saber qué pasó."],
], alto=0.52, tam=12)
codigo(s, 0.5, 4.95, 8.6, 1.7, [
    "public void afterCompletion(int estado) {",
    "  if (estado == Status.STATUS_COMMITTED) return;",
    "  pasarela.reembolsarCompensacion(idPago, \"Reversión automática…\", ref);",
    "}"], tam=11.5, titulo="negocio/CompensacionDePago.java · se ejecuta cuando termina la transacción")
tarjeta(s, 9.35, 1.7, 3.45, 4.85, "En vivo", [
    "1. Administrativo → Pagos → «Armar falla antes del commit».",
    "2. El paciente reserva y paga con 4111…1111.",
    "3. Mensaje: se revirtió todo. El turno sigue en espera.",
    "4. Consola de la pasarela: el cobro figura REEMBOLSADO.",
    "5. No llegó ningún aviso: el mensaje no salió.",
    "6. Reintenta y confirma: el hold no se perdió."], color=CELESTE, tam=12.5)

# 9 · JWT -------------------------------------------------------------------------------------------
s = diapositiva("Seguridad: el ingreso con token (JWT)", ["Sergio"], "12:30 – 16:00", en_vivo=True)
for i, (parte, contenido, nota) in enumerate([
    ("Cabecera", '{"alg":"HS256","typ":"JWT"}', "Qué algoritmo firma"),
    ("Contenido", '{"sub":"juan.perez@…","rol":"PACIENTE",\n "iss":"mediconecta","exp":…}', "Quién es, qué rol tiene y hasta cuándo vale (30 min)"),
    ("Firma", "HMAC-SHA256(cabecera.contenido, secreto)", "Sólo el servidor conoce el secreto"),
]):
    x = 0.5 + i * 4.18
    caja(s, x, 1.3, 3.95, 2.2, GRIS)
    texto(s, x + 0.2, 1.4, 3.5, 0.4, parte, tam=18, negrita=True, color=ACENTO)
    texto(s, x + 0.2, 1.9, 3.6, 0.9, contenido, tam=11.5, fuente="Consolas")
    texto(s, x + 0.2, 2.85, 3.6, 0.6, nota, tam=12.5, color=TENUE)
caja(s, 0.5, 3.7, 12.3, 0.6, AZUL)
texto(s, 0.5, 3.7, 12.3, 0.6, "Cualquiera puede leer el token. Nadie puede modificarlo sin el secreto.", tam=17,
      negrita=True, color=BLANCO, alin=PP_ALIGN.CENTER, centro_v=True)
for i, (caso, codigo_http, color) in enumerate([
    ("Sin token", "401", ROJO), ("Login correcto", "200 + token", VERDE), ("Con el token", "200", VERDE),
    ("Rol cambiado a mano", "401", ROJO), ("Token válido, rol sin permiso", "403", NARANJA)]):
    x = 0.5 + i * 2.5
    caja(s, x, 4.55, 2.3, 1.35, GRIS)
    texto(s, x, 4.62, 2.3, 0.55, caso, tam=12.5, alin=PP_ALIGN.CENTER)
    texto(s, x, 5.15, 2.3, 0.6, codigo_http, tam=20, negrita=True, color=color, alin=PP_ALIGN.CENTER)
texto(s, 0.5, 6.15, 10, 0.4, "python pruebas/demo_api_jwt_409.py --sin-cancelar", tam=14, fuente="Consolas", color=TENUE)

# 10 · JWT código -----------------------------------------------------------------------------------
s = diapositiva("JWT: cómo está hecho", ["Sergio"], "12:30 – 16:00")
codigo(s, 0.5, 1.25, 7.3, 5.3, [
    'String[] partes = token.trim().split("\\\\.", -1);',
    "if (partes.length != 3) return Optional.empty();",
    "",
    'if (!"HS256".equals(texto(cabecera, "alg")))',
    "    return Optional.empty();          // rechaza alg: none",
    "",
    'byte[] esperada = firmar(partes[0] + "." + partes[1]);',
    "if (!MessageDigest.isEqual(esperada, recibida))",
    "    return Optional.empty();          // contenido alterado",
    "",
    "if (reloj.instant().getEpochSecond() >= vence)",
    "    return Optional.empty();          // vencido",
    "",
    "return Optional.of(new IdentidadJwt(email, rol, vence));"],
       tam=12, titulo="seguridad/CodificadorJwt.java · validar() (línea 69)")
for i, (titulo, cuerpo) in enumerate([
    ("1 · Login", "AuthResource valida contra el mismo almacén de identidades que la web y emite el token. Cinco "
                  "contraseñas incorrectas bloquean la cuenta 15 minutos."),
    ("2 · Cada pedido", "MecanismoAutenticacion lee Authorization: Bearer, valida y le pasa usuario y rol al contenedor."),
    ("3 · Autorización", "@RolesAllowed en los EJB: el 403 lo decide el contenedor, no nuestro código. "
                         "TurnosResource (línea 33): paciente y profesional."),
]):
    tarjeta(s, 8.05, 1.25 + i * 1.8, 4.75, 1.65, titulo, [cuerpo], color=CELESTE if i == 2 else GRIS, tam=12.5,
            tam_titulo=15)

# 11 · State ----------------------------------------------------------------------------------------
s = diapositiva("Las reglas del turno (patrón State)", ["Agustín"], "16:00 – 19:00")
texto(s, 0.5, 1.15, 12.3, 0.4, "Operaciones admitidas en cada estado", tam=16, negrita=True, color=ACENTO)
SI, NO = ("✓", {"color": VERDE, "negrita": True, "tam": 16}), ("✗", {"color": "9AA3B5", "tam": 16})
C409 = ("409", {"color": ROJO, "negrita": True, "tam": 15})
tabla(s, 0.5, 1.65, [3.0, 2.3, 2.3, 2.3, 2.4], [
    ["", "reservar", "confirmar", "liberar", "cancelar"],
    [("Disponible", {"negrita": True}), SI, NO, NO, NO],
    [("En espera", {"negrita": True}), NO, SI, SI, C409],
    [("Confirmado", {"negrita": True}), NO, NO, NO, SI],
    [("Cancelado", {"negrita": True}), NO, NO, NO, C409],
], alto=0.62, tam=14)
tarjeta(s, 0.5, 4.75, 6.05, 1.85, "¿Por qué no un if en cada servicio?", [
    "La regla queda repartida y un servicio nuevo puede olvidarla.",
    "Con State, una transición inválida es imposible por diseño."], tam=14)
tarjeta(s, 6.75, 4.75, 6.05, 1.85, "Quién puede cancelar no lo decide el State", [
    "El rol: @RolesAllowed (paciente, profesional, administrativo).",
    "La titularidad: el servicio. Un turno ajeno responde 404.",
    "Si se puede o no: el estado. Para los tres roles, igual."], color=CELESTE, tam=13)

# 12 · 409 ------------------------------------------------------------------------------------------
s = diapositiva("Cuando el turno dice que no: error 409", ["Agustín"], "16:00 – 19:00", en_vivo=True)
codigo(s, 0.5, 1.2, 7.4, 1.35, [
    "default EstadoTurno cancelar(Turno turno) {",
    '    throw invalida("cancelar");',
    "}"], tam=11.5, titulo="modelo/EstadoDeTurno.java (línea 21) — todo es inválido por defecto")
codigo(s, 0.5, 2.7, 7.4, 2.15, [
    "if (turno == null || !puedeCancelar(quien, turno))",
    "    throw new TurnoInexistenteException();   // 404",
    "turno.cancelar(quien.getRol());   // decide el State",
    "turnoDAO.actualizar(turno);"], tam=11.5,
       titulo="negocio/ProcesoCancelacionTurno.java (línea 64) — sin ningún if sobre el estado")
codigo(s, 0.5, 5.0, 7.4, 1.5, [
    "if (causa instanceof TransicionInvalidaException t)",
    '    return ErrorApi.de(409, "transicion_invalida", …);'], tam=11.5,
       titulo="api/MapeadorDeExcepciones.java (línea 78)")
caja(s, 8.15, 1.2, 4.65, 2.1, GRIS)
texto(s, 8.35, 1.3, 4.3, 0.4, "POST /turnos/52/cancelacion", tam=12.5, fuente="Consolas", negrita=True)
texto(s, 8.35, 1.75, 4.3, 0.6, "200 · estado: CANCELADO", tam=20, negrita=True, color=VERDE)
texto(s, 8.35, 2.45, 4.3, 0.7, "Confirmado → Cancelado es una transición válida.", tam=12.5, color=TENUE)
caja(s, 8.15, 3.45, 4.65, 2.3, "FDECEA")
texto(s, 8.35, 3.55, 4.3, 0.4, "POST /turnos/52/cancelacion   (otra vez)", tam=12.5, fuente="Consolas", negrita=True)
texto(s, 8.35, 4.0, 4.3, 0.6, "409 Conflict", tam=22, negrita=True, color=ROJO)
texto(s, 8.35, 4.65, 4.3, 1.0, ["«No se puede cancelar un turno cancelado.»",
                                "La transacción se revierte: no queda ningún efecto."], tam=12.5, color=TENUE, espacio=4)
texto(s, 8.15, 5.9, 4.65, 0.7, "El mismo 409 recibe el administrativo: decide el estado, no quién pide.", tam=12.5,
      negrita=True, color=ACENTO)

# Datos ---------------------------------------------------------------------------------------------
s = diapositiva("Los datos: cómo se guardan y cómo se protegen", ["Máximo"])
tarjeta(s, 0.5, 1.25, 6.05, 2.75, "Las consultas, en un solo lugar", [
    guia("DAO:", "la única clase que habla con la base. Hay 12."),
    guia("Ejemplo:", "TurnoDAO.buscarDisponibles arma la agenda que vio el paciente."),
    guia("Filtra:", "sólo horarios disponibles y futuros."),
    guia("Con parámetros:", "nunca pegando texto. Así no hay inyección SQL."),
], tam=14.5)
tarjeta(s, 6.75, 1.25, 6.05, 2.75, "Dos pacientes, un mismo horario", [
    guia("El problema:", "los dos tocan «Reservar» al mismo tiempo."),
    guia("@Version:", "un contador en el turno que sube con cada cambio."),
    guia("Resultado:", "el primero guarda; al segundo la base lo rechaza."),
    guia("Garantía:", "un horario nunca se reserva dos veces."),
], tam=14.5)
caja(s, 0.5, 4.2, 12.3, 2.45, "FFF3E6")
texto(s, 0.7, 4.28, 11.8, 0.4, "Un problema de seguridad que encontramos y cerramos", tam=16, negrita=True, color=NARANJA)
texto(s, 0.7, 4.75, 5.7, 1.85, [
    guia("Dónde:", "la base está en Supabase.", NARANJA),
    guia("Qué pasaba:", "Supabase publica las tablas por una API propia, además de la conexión normal.", NARANJA),
    guia("El riesgo:", "se podían leer sin pasar por nuestra aplicación. Usuarios e historia clínica incluidos.", NARANJA),
], tam=13, espacio=4)
texto(s, 6.9, 4.75, 5.7, 1.85, [
    guia("La solución:", "activar Row Level Security (RLS) en las 13 tablas.", VERDE),
    guia("Sin políticas:", "esa API ya no devuelve ninguna fila.", VERDE),
    guia("La aplicación:", "sigue igual; entra como dueña de las tablas.", VERDE),
    guia("Hoy:", "la única puerta a los datos es la aplicación, con sus roles.", VERDE),
], tam=13, espacio=4)

# Decisiones -----------------------------------------------------------------------------------------
s = diapositiva("Las decisiones: por qué lo hicimos así", ["Máximo"])
texto(s, 0.5, 1.12, 12.3, 0.45,
      "Por cada solución que aplicamos hay una página que explica el problema, qué elegimos y qué descartamos (ADR). Son 18.",
      tam=14.5, color=TENUE)
PATRONES = ["DAO", "Session Facade", "Factory", "State", "Strategy", "Chain of Responsibility", "Adapter / Gateway",
            "Circuit Breaker + Retry", "Builder", "Template Method", "Publish-Subscribe", "Point-to-Point",
            "Transactional Client", "Intercepting Filter", "Singleton", "Transfer Object", "JWT", "RLS"]
for i, patron in enumerate(PATRONES):
    fila, col = divmod(i, 3)
    seguridad = patron in ("JWT", "RLS")
    caja(s, 0.5 + col * 2.05, 1.75 + fila * 0.5, 1.97, 0.42, "E8F6EE" if seguridad else CELESTE)
    texto(s, 0.5 + col * 2.05, 1.75 + fila * 0.5, 1.97, 0.42, patron, tam=10.5, alin=PP_ALIGN.CENTER, centro_v=True)
texto(s, 0.5, 4.8, 6.1, 0.4, "16 de diseño e integración · 2 de seguridad (en verde)", tam=11.5, color=TENUE)
tarjeta(s, 6.85, 1.75, 5.95, 2.95, "Qué responde cada uno", [
    guia("Contexto:", "qué problema teníamos."),
    guia("Decisión:", "qué hicimos."),
    guia("Alternativas:", "qué descartamos y por qué."),
    guia("Consecuencias:", "qué ganamos y qué nos cuesta."),
    guia("Código y prueba:", "dónde está y cómo se verifica."),
], tam=13.5)
caja(s, 0.5, 5.25, 12.3, 1.4, GRIS)
texto(s, 0.7, 5.32, 11.9, 0.4, "Un ejemplo: la obra social se cae seguido", tam=15, negrita=True, color=ACENTO)
texto(s, 0.7, 5.75, 11.9, 0.9, [
    guia("Elegimos:", "cortar la llamada a los 3 segundos, reintentar una vez y, tras tres fallas, dejar de llamarla 30 segundos."),
    guia("Descartamos:", "una biblioteca que no viene en nuestro servidor.  Nos cuesta: mantener ese código nosotros."),
], tam=13, espacio=3)

# 14 · Pruebas --------------------------------------------------------------------------------------
s = diapositiva("Las pruebas: cómo sabemos que anda", ["Los tres"], "21:00 – 22:30", en_vivo=True)
NUMEROS = [("70", "pruebas unitarias", "JUnit 5 · sin servidor"),
           ("58", "integración de punta a punta", "SOAP, REST, cola, tópico, falla y 409"),
           ("31", "cancelación por rol", "paciente, profesional, administrativo y API"),
           ("61", "seguridad y monitor", "bloqueo, HTTPS, inyección SQL, DLQ, logs")]
for i, (numero, que, detalle) in enumerate(NUMEROS):
    x = 0.5 + i * 3.13
    caja(s, x, 1.25, 2.93, 2.05, CELESTE if i < 2 else GRIS)
    texto(s, x, 1.3, 2.93, 0.9, numero, tam=40, negrita=True, color=ACENTO, alin=PP_ALIGN.CENTER)
    texto(s, x, 2.2, 2.93, 0.4, que, tam=13.5, negrita=True, alin=PP_ALIGN.CENTER)
    texto(s, x + 0.1, 2.6, 2.73, 0.6, detalle, tam=11, color=TENUE, alin=PP_ALIGN.CENTER)
texto(s, 0.5, 3.5, 12.3, 0.4,
      "Todas aprobadas sobre la versión que entregamos. Las de punta a punta usan un navegador real. Un test por integrante:",
      tam=14, color=TENUE)
for i, (quien, clase, que) in enumerate([
    ("Sergio", "CodificadorJwtTest", "Cambia el rol dentro del token y verifica que se rechace."),
    ("Agustín", "TurnoEstadoTest", "Las doce transiciones inválidas, cada una con su mensaje."),
    ("Máximo", "MapeadorDeExcepcionesTest", "Una transición inválida se traduce a 409."),
]):
    x = 0.5 + i * 4.18
    caja(s, x, 4.05, 3.95, 1.9, GRIS)
    pastilla(s, x + 0.2, 4.2, 1.25, quien, QUIEN[quien])
    texto(s, x + 0.2, 4.7, 3.6, 0.4, clase, tam=14, negrita=True, fuente="Consolas")
    texto(s, x + 0.2, 5.15, 3.6, 0.75, que, tam=12.5, color=TENUE)
texto(s, 0.5, 6.15, 6, 0.4, "mvn test", tam=16, fuente="Consolas", negrita=True, color=ACENTO)

# Dónde estamos --------------------------------------------------------------------------------------
s = diapositiva("Dónde estamos: qué está hecho y qué falta", ["Máximo"])
tarjeta(s, 0.5, 1.25, 6.05, 3.7, "Hecho y funcionando", [
    guia("El recorrido completo:", "reservar, pagar, confirmar, cancelar."),
    guia("Integraciones:", "obra social, pagos y videoconsulta."),
    guia("Mensajería:", "autorizaciones y avisos en segundo plano."),
    guia("Ante fallas:", "se deshace todo y se devuelve el cobro."),
    guia("Seguridad:", "token, roles, HTTPS y bloqueo por intentos."),
    guia("Monitor:", "se ve qué procesó cada aviso."),
    guia("Pruebas:", "70 unitarias y todo el recorrido con navegador."),
], color="E8F6EE", tam=14.5, color_titulo=VERDE)
tarjeta(s, 6.75, 1.25, 6.05, 3.7, "Lo que falta", [
    guia("Cerrar sesión de un token:", "hoy vale 30 minutos y no se puede anular antes.", ROJO),
    guia("Reembolso con pagos caído:", "si falla la confirmación y pagos no responde, la devolución se hace a mano.", ROJO),
    guia("Certificado:", "el HTTPS usa uno de prueba, sirve sólo en nuestra máquina.", ROJO),
    guia("Sistemas externos:", "son simuladores nuestros, no los reales.", ROJO),
], color="FDECEA", tam=14.5, color_titulo=ROJO)
tarjeta(s, 0.5, 5.15, 6.05, 1.5, "Cómo nos organizamos", [
    guia("Trello:", "cada tarea con su responsable."),
    guia("Reparto:", "una capa cada uno; las pruebas, entre los tres."),
], tam=13, tam_titulo=14.5)
tarjeta(s, 6.75, 5.15, 6.05, 1.5, "Lo que sigue", [
    guia("Para la entrega final:", "resolver los pendientes de arriba."),
    guia("Ninguno", "impide usar el sistema hoy."),
], tam=13, tam_titulo=14.5)

# Cierre ---------------------------------------------------------------------------------------------
s = prs.slides.add_slide(prs.slide_layouts[6])
caja(s, 0, 0, 13.333, 7.5, AZUL, MSO_SHAPE.RECTANGLE)
texto(s, 0.9, 0.8, 11.5, 0.9, "En resumen", tam=40, negrita=True, color=BLANCO)
texto(s, 0.9, 2.0, 11.5, 3.6, [
    "✓  Un turno completo: de la reserva al cobro, con los avisos",
    "✓  Conectado a la obra social, a pagos y a la videoconsulta",
    "✓  Si algo falla a mitad, se deshace y se devuelve la plata",
    "✓  Sólo entra quien debe, y hace sólo lo que su rol permite",
    "✓  Cada decisión está escrita y cada afirmación tiene su prueba",
    "✓  Sabemos qué falta y lo tenemos anotado"], tam=20, color=BLANCO, espacio=10)
texto(s, 0.9, 5.9, 11.5, 0.8, "¿Preguntas?", tam=32, negrita=True, color="DCE6FF")

if __name__ == "__main__":
    destino = os.path.join(os.path.dirname(os.path.abspath(__file__)), "MediConecta_Presentacion_16-11.pptx")
    prs.save(destino)
    print("Generado:", destino, "·", len(prs.slides), "diapositivas")
