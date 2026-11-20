"""Genera docs/final/diseno-patrones-y-seguridad-final.html: el documento de diseño de patrones y seguridad tal como
quedó construido (la versión del 18/10, en la raíz del repositorio, era el plan y no se modifica).

Toma la hoja de estilos del documento técnico final para que los dos se vean igual.
Uso: python docs/final/generar_diseno_final.py  (y después el PDF con el índice, como los demás documentos)
"""
import html
import pathlib

AQUI = pathlib.Path(__file__).resolve().parent
cabeza = (AQUI / "documento-tecnico-final.html").read_text(encoding="utf-8").split("<body>")[0]
cabeza = cabeza.replace(cabeza[cabeza.index("<title>"):cabeza.index("</title>") + 8],
                        "<title>MediConecta: diseño de patrones y seguridad, versión final</title>")


def e(t):
    return html.escape(t, quote=False)


def m(t):
    return '<span class="mono">%s</span>' % e(t)


def tabla(num, titulo, columnas, filas, anchos=None, nota=None):
    anchos = anchos or [None] * len(columnas)
    th = "".join('<th%s>%s</th>' % (' style="width:%s"' % a if a else "", c) for c, a in zip(columnas, anchos))
    tr = "".join("<tr>%s</tr>" % "".join("<td>%s</td>" % c for c in f) for f in filas)
    pie = '<div class="nota"><em class="etq">Nota.</em> %s</div>' % nota if nota else ""
    return ('<div class="tabla"><div class="num">Tabla %d</div><div class="tit">%s</div><table><thead><tr>%s</tr></thead>'
            '<tbody>%s</tbody></table>%s</div>' % (num, titulo, th, tr, pie))


indice = [("alcance", "1. Alcance y diferencias con el diseño de octubre", 1), ("mapa", "2. Mapa de patrones", 1),
          ("descartados", "3. Patrones evaluados y descartados", 1), ("seguridad", "4. Diseño de seguridad", 1),
          ("matriz", "4.1 Roles y matriz de permisos", 2), ("autenticacion", "4.2 Autenticación", 2),
          ("autorizacion", "4.3 Autorización declarativa y por titularidad", 2),
          ("mensajeria", "4.4 Seguridad en la mensajería", 2), ("datos", "4.5 Protección de los datos", 2),
          ("errores", "4.6 Validación, errores y cabeceras", 2), ("owasp", "4.7 OWASP Top 10: mitigación y verificación", 2),
          ("trazabilidad", "5. Trazabilidad con el checklist de la cátedra", 1), ("ia", "Declaración de uso de IA", 1)]

p = []
p.append('''<section class="caratula">
  <div class="institucion-cabecera">
    <div class="sigla">UADE</div>
    <div class="nombre-uni">UNIVERSIDAD ARGENTINA DE LA EMPRESA</div>
    <div class="facultad">Facultad de Ingeniería y Ciencias Exactas</div>
    <div class="facultad">Departamento de Tecnología Informática</div>
    <div class="filete"></div>
  </div>
  <div class="titulo">Trabajo Práctico: Desarrollo de Aplicaciones II</div>
  <div class="subtitulo">MediConecta: diseño de patrones y seguridad</div>
  <div>Versión final, tal como quedó construido</div>
  <div class="bloque"><div>Grupo 4</div><div>Agustín Bodas, Máximo Bonarrico y Sergio Casanova</div></div>
  <div class="bloque">
    <div>Universidad Argentina de la Empresa</div>
    <div>3.4.218 Desarrollo de Aplicaciones II, Comisión Lunes TT</div>
    <div>Prof. Matías Ruiz</div>
    <div>Versión final: 20 de noviembre de 2026</div>
    <div>(diseño original: 18 de octubre de 2026)</div>
  </div>
</section>''')
p.append('<section class="indice salto"><h1>Índice</h1><ul>%s</ul></section>' % "".join(
    '<li%s><a href="#%s">%s</a><span class="relleno"></span><span class="pag" data-para="%s"></span></li>'
    % (' class="n2"' if n == 2 else "", i, e(t), i) for i, t, n in indice))

# 1. Alcance
diferencias = [
    ["Fachada de turnos", m("TurnoFacade") + " (@Stateless) y " + m("TurnoService"),
     m("ServicioDeTurnosBean") + " (@Stateful: conserva el turno en espera entre pantallas) y " + m("ProcesoConfirmacionTurno") + " (@Stateless, la transacción)",
     "ADR-0002"],
    ["EJB con estado", m("ReservaEnCurso") + ", un asistente de reserva paso a paso",
     "El mismo " + m("ServicioDeTurnosBean") + ": la reserva retiene el turno 5 minutos y la confirmación lo cobra", "ADR-0002"],
    ["Colas", m("cola.autorizaciones") + " y " + m("cola.revalidacion"),
     "Sólo " + m("cola.autorizaciones") + ": la revalidación no hizo falta, porque la cobertura se consulta en cada confirmación", "ADR-0012"],
    ["JWT de la API", "MicroProfile JWT",
     "JWT propio con HS256 (" + m("CodificadorJwt") + "): WildFly 41 sin el perfil de MicroProfile y una sola clave simétrica", "ADR-0017"],
    ["Resiliencia", "MicroProfile Fault Tolerance",
     "Circuit Breaker, Retry y Fallback propios (" + m("CircuitBreaker") + ", " + m("Reintento") + "), por la misma razón y para poder probarlos sin contenedor", "ADR-0008"],
    ["Autenticación web", "BASIC sobre TLS", "Formulario propio con sesión del contenedor; la API usa JWT. Los dos validan contra el mismo identity store", "ADR-0017"],
    ["Rol AUDITOR", "Consultaba la auditoría",
     "No se implementó: cada paciente ve quién accedió a su historia (" + m("accesosAMiHistoria") + "), y la auditoría completa queda en la base", "—"],
    ["Strategy de lista de espera", m("PoliticaListaEspera"), "No se implementó: no hay lista de espera en el alcance", "—"],
    ["Dependency-Check", "En el build de Maven, con el reporte en el repositorio",
     "En el pipeline (perfil " + m("dependencias") + "), junto con Trivy; el reporte queda como artefacto de cada corrida", "—"],
]
p.append('<section class="salto"><h1 id="alcance">1. Alcance y diferencias con el diseño de octubre</h1>'
         '<p>Este documento describe los patrones de diseño y el diseño de seguridad de MediConecta tal como quedaron '
         'construidos al cierre del trabajo. Reemplaza, como descripción del sistema, al documento de diseño del 18 de '
         'octubre (' + m("Diseno_Patrones_y_Seguridad_MediConecta_Grupo4.pdf") + '), que era el plan: lo conservamos en el '
         'repositorio porque muestra de dónde partimos. Cada patrón tiene su registro de decisión en ' + m("docs/adr/") +
         ' (24 en total), con el problema, las alternativas descartadas y las consecuencias; acá no repetimos ese detalle, '
         'sino que lo ordenamos y lo vinculamos con el código y con las pruebas.</p>'
         '<p>Al construirlo, varias decisiones cambiaron respecto del plan. La Tabla 1 las reúne para que quien lea los dos '
         'documentos no encuentre contradicciones sin explicar.</p>' +
         tabla(1, "Qué cambió entre el diseño de octubre y lo construido",
               ["Tema", "Diseño de octubre", "Lo construido", "Decisión"], diferencias, ["16%", "24%", None, "10%"]) +
         "</section>")

# 2. Mapa de patrones
J, G, I, R, S = "Java EE", "GoF", "Integración", "Resiliencia", "Transacciones"
patrones = [
    ["Data Access Object", J, m("dao/*DAO") + " (15 DAO, uno por agregado)", "14/09", "0001"],
    ["Transfer Object", J, m("api/dto/*Api") + ", " + m("negocio/dto/*") + " (" + m("FichaPaciente") + ", " + m("FilaMonitorDTO") + ")", "09/11", "0016"],
    ["Session Facade", J, m("ServicioDeTurnosBean") + " (@Stateful) sobre " + m("ProcesoConfirmacionTurno"), "14/09", "0002"],
    ["Intercepting Filter", J, m("AuditoriaInterceptor") + " con " + m("@Auditado") + " sobre la historia clínica", "09/11", "0014"],
    ["Singleton", G + " / EJB", m("CatalogoPrestaciones") + " (@Singleton, @Startup, @Lock(READ))", "09/11", "0015"],
    ["Factory", G, m("UsuarioFactory") + ": crea paciente, profesional o administrativo según el rol", "14/09", "0003"],
    ["Builder", G, m("EventoTurno.Builder"), "19/10", "0009"],
    ["Template Method y Factory Method", G, m("ProcesadorNotificacion") + " con " + m("ProcesadorEmail") + " y " + m("ProcesadorAvisoProfesional"), "19/10", "0010"],
    ["Adapter y Gateway", I, m("CoberturaSoapAdapter") + ", " + m("PasarelaPagosRestAdapter") + ", " + m("VideoconsultaRestAdapter") + " detrás de sus " + m("*Gateway"), "09/11", "0007"],
    ["Chain of Responsibility", G, m("CadenaDeValidacion") + " y los eslabones " + m("ValidadorReserva"), "09/11", "0006"],
    ["State", G, m("EstadoDeTurno") + " con " + m("TurnoDisponible") + ", " + m("TurnoEnHold") + ", " + m("TurnoConfirmado") + " y " + m("TurnoCancelado") + "; 409 ante una transición inválida", "09/11", "0004"],
    ["Strategy", G, m("CalculoCopago") + " (" + m("CopagoPorcentual") + ", " + m("CopagoMontoFijo") + ", " + m("CopagoExento") + ", " + m("TarifaParticular") + "), elegida por " + m("SelectorCalculoCopago"), "09/11", "0005"],
    ["Decorator", G + " / CDI", m("PacienteEnmascarado") + " (@Decorator) sobre " + m("ServicioDePacientes"), "Final", "0023"],
    ["Circuit Breaker, Retry y Fallback con caché", R, m("CircuitBreaker") + ", " + m("Reintento") + "; si el legado no responde, la cotización usa la última validación exitosa (24 h) o queda pendiente", "09/11", "0008"],
    ["Publish-Subscribe, Durable Subscriber y Selective Consumer", I, m("topico.turnos") + " con tres suscriptores durables; Facturación con selector por tipo de evento", "19/10", "0011"],
    ["Idempotent Receiver", I, "Tabla " + m("evento_procesado") + " con clave única suscriptor:evento", "19/10", "0011"],
    ["Point-to-Point y Competing Consumers", I, m("cola.autorizaciones") + " con " + m("AutorizacionConsumer"), "09/11", "0012"],
    ["Dead Letter Channel y Message Expiration", I, "DLQ por " + m("address-setting") + " de WildFly; TTL de 24 h en las solicitudes", "19/10", "0011, 0012"],
    ["Transactional Client", I + " / " + S, "Envío JMS dentro de la transacción JTA (fábrica XA) en " + m("ProcesoConfirmacionTurno"), "09/11", "0013"],
    ["Transacción compensatoria", S, m("CompensacionDePago") + " (Synchronization): reembolsa el cobro si la confirmación no hace commit", "09/11", "0013"],
    ["Bandeja de salida con reintentos (outbox)", R, m("ReintegroPendiente") + ", " + m("ServicioDeReintegrosBean") + " y " + m("ReintentadorDeReintegros"), "Final", "0024"],
    ["Clave de idempotencia", I, m("Idempotencia") + " y " + m("PedidoIdempotente") + ": un POST repetido con la misma Idempotency-Key devuelve la misma respuesta", "Final", "—"],
    ["Bloqueo optimista con ETag", I, m("@Version") + " del turno expuesto como ETag; If-Match desactualizado responde 412", "Final", "0004"],
]
p.append('<section class="salto"><h1 id="mapa">2. Mapa de patrones</h1>'
         '<p>La Tabla 2 lista los patrones aplicados, con las clases que cumplen cada rol, la entrega en que se '
         'incorporaron y su registro de decisión. Todos están en el código y tienen pruebas: las unitarias cubren la '
         'lógica de los patrones sin contenedor y las de integración, los que dependen del servidor (la mensajería, la '
         'transacción y la compensación). El detalle de cada prueba está en el documento del QA.</p>' +
         tabla(2, "Patrones aplicados en MediConecta", ["Patrón", "Familia", "Dónde está", "Desde", "ADR"],
               patrones, ["22%", "11%", None, "8%", "8%"],
               "Desde: fecha de la decisión (14/09, Entrega 1; 19/10, Entrega Parcial N.º 2; 09/11, Entrega Obligatoria N.º 2). "
               "Final: agregados para la entrega final. Los diagramas de clases de los patrones están en el "
               "documento maestro de diagramas, versión final (Figuras 10, 17 y 21).") + "</section>")

# 3. Descartados
descartados = [
    ["Observer", "Lo provee JMS con el tópico; implementarlo a mano duplicaría lo que resuelve el broker."],
    ["Service Locator y Business Delegate", "Reemplazados por la inyección de CDI y @EJB. No hay clientes remotos de EJB."],
    ["Command y Memento", "Sólo aportan si hay que deshacer acciones, y no es un requerimiento. El historial queda en la auditoría."],
    ["Abstract Factory", "Hay una sola obra social integrada; se reevalúa si se suman varias con familias de componentes propias."],
    ["Correlation Identifier", "El consumidor de autorizaciones resuelve contra el legado de forma sincrónica: no hay respuesta que correlacionar."],
    ["Claim Check", "Reduciría los datos en el broker a cambio de una consulta por evento; se prefirió un evento con datos mínimos y sin datos clínicos."],
    ["MicroProfile Fault Tolerance y MicroProfile JWT", "Planificados en octubre y reemplazados por implementaciones propias (ADR-0008 y ADR-0017)."],
    ["Visitor, Prototype, Flyweight y Composite", "No hay en el dominio un problema concreto que resuelvan."],
]
p.append('<section><h1 id="descartados">3. Patrones evaluados y descartados</h1>' +
         tabla(3, "Patrones evaluados y descartados", ["Patrón", "Motivo"], descartados, ["30%", None]) + "</section>")

# 4. Seguridad
matriz = [
    ["Registrarse", "Sí", "—", "—", "—"],
    ["Reservar y confirmar un turno", "Sí, para sí mismo", "No", "No", "No"],
    ["Cancelar un turno", "Los propios (*)", "Los de su agenda (*)", "Cualquiera", "No"],
    ["Ver la historia clínica", "La propia (*)", "De pacientes con turno con él (*)", "No", "No"],
    ["Cargar diagnóstico o receta", "No", "Sobre un turno confirmado suyo (*)", "No", "No"],
    ["Ver la ficha del paciente", "No", "Completa si tiene turno con él; si no, sin datos clínicos (*)", "Sin datos clínicos", "No"],
    ["Ver pagos", "Los propios (*)", "No", "Todos, y los reclamos a obras sociales", "No"],
    ["Resolver reintegros pendientes", "No", "No", "Sí", "No"],
    ["Dar de alta y desbloquear usuarios", "No", "No", "Sí", "No"],
    ["Pedir autorizaciones, facturar y reintegrar", "No", "No", "No", "Sí, desde los MDB (@RunAs)"],
]
owasp = [
    ["A01 Control de acceso roto", "@RolesAllowed según la matriz y titularidad contra IDOR; un recurso ajeno responde 404 como uno inexistente", "CP-18, CP-43, CP-55"],
    ["A02 Fallas criptográficas", "HTTPS con HSTS; PBKDF2-SHA256 de 600.000 iteraciones; AES-256-GCM en columnas con la clave en Elytron", "CP-50, CP-85"],
    ["A03 Inyección", "JPA siempre con parámetros; Bean Validation; JSF escapa la salida; el canal SOAP rechaza las entidades externas (XXE)", "CP-49, CP-53, CP-56"],
    ["A04 Diseño inseguro", "Matriz de permisos y minimización de datos definidas antes del código; eventos sin datos clínicos", "CP-84"],
    ["A05 Configuración incorrecta", "Rol guest del broker eliminado; cabeceras de seguridad y CORS cerrados; sin trazas en las respuestas", "CP-51, CP-48"],
    ["A06 Componentes vulnerables", "Trivy y OWASP Dependency-Check en cada corrida del pipeline", "CP-82"],
    ["A07 Fallas de autenticación", "Límite de intentos con 429 y bloqueo de 15 minutos; JWT de 30 minutos, revocable al cerrar sesión", "CP-47, CP-73"],
    ["A08 Integridad de software y datos", "Sólo la aplicación publica en el tópico; gitleaks sobre todo el historial; dependencias con versión fija", "CP-81"],
    ["A09 Registro y monitoreo", "Interceptor de auditoría sobre la historia clínica; bitácora de integraciones; logs sin datos personales", "CP-54"],
    ["A10 SSRF", "Los destinos salientes son fijos en la configuración y nunca se arman con datos del usuario", "—"],
]
p.append('<section class="salto"><h1 id="seguridad">4. Diseño de seguridad</h1>'
         '<p>MediConecta trata datos de salud. La Ley 25.326 de Protección de Datos Personales los define como datos '
         'sensibles (art. 2) y regula su tratamiento (art. 8), y la Ley 26.529 de Derechos del Paciente establece la '
         'confidencialidad de la historia clínica. Las decisiones de esta sección responden a ese marco: control de acceso '
         'por rol y por titularidad, cifrado en tránsito y en reposo, minimización de los datos que circulan y trazabilidad '
         'de los accesos.</p>'
         '<h2 id="matriz">4.1 Roles y matriz de permisos</h2>'
         '<p>Hay tres roles de persona (PACIENTE, PROFESIONAL y ADMINISTRATIVO) y un rol técnico, SISTEMA, que sólo usan '
         'los consumidores de mensajes. Los casilleros con (*) no se resuelven sólo con el rol: además se verifica que el '
         'recurso sea de quien lo pide (4.3).</p>' +
         tabla(4, "Matriz de permisos", ["Operación", "Paciente", "Profesional", "Administrativo", "Sistema"], matriz,
               ["24%", None, None, None, "14%"]) +
         '<h2 id="autenticacion">4.2 Autenticación</h2>'
         '<p>Un solo ' + m("HttpAuthenticationMechanism") + ' (' + m("MecanismoAutenticacion") + ') atiende los dos caminos: '
         'la web usa un formulario propio y la sesión del contenedor, y la API REST usa un JWT firmado con HS256 que dura 30 '
         'minutos. Los dos validan contra el mismo identity store sobre la tabla de usuarios, con contraseñas en PBKDF2-SHA256 '
         'de 600.000 iteraciones (ADR-0021). Cinco intentos fallidos seguidos bloquean la cuenta 15 minutos y la API responde '
         '429 con ' + m("Retry-After") + '; el administrativo puede desbloquearla. Al cerrar sesión en la API, el token se revoca '
         '(se guarda su identificador hasta que vence) y deja de servir aunque no haya vencido (ADR-0019). Las cuentas que '
         'crea el administrativo nacen con una contraseña temporal que hay que cambiar en el primer ingreso.</p>'
         '<h2 id="autorizacion">4.3 Autorización declarativa y por titularidad</h2>'
         '<p>La autorización declarativa (' + m("@RolesAllowed") + ', ' + m("@PermitAll") + ' y ' + m("@DeclareRoles") +
         ' en los EJB y en los recursos REST) decide si un rol puede ejecutar una operación. No alcanza para la regla '
         '«un paciente sólo opera sus propios turnos»: cambiando el id en la URL, un paciente autenticado podría pedir el '
         'turno de otro (IDOR). Esa regla se resuelve en el servicio con el usuario autenticado: el turno, la historia o el '
         'pago se buscan junto con su titular, y si no coincide la respuesta es la misma que si no existiera (404 en la '
         'API), para no revelar qué ids hay. La ficha del paciente aplica la misma idea con un Decorator: '
         + m("PacienteEnmascarado") + ' quita los datos clínicos salvo que quien la pida sea un profesional con un turno '
         'confirmado con ese paciente (ADR-0023).</p>'
         '<h2 id="mensajeria">4.4 Seguridad en la mensajería</h2>'
         '<p>Un MDB no tiene un usuario que lo invoque, así que los consumidores corren con ' + m('@RunAs("SISTEMA")') +
         ', el único rol que puede pedir autorizaciones o reintegrar pagos. En el broker se eliminó el rol guest que WildFly '
         'trae por defecto, que podía mandar y leer en cualquier destino, y cada destino tiene sus permisos: sólo la '
         'aplicación publica en el tópico de turnos y sólo los consumidores leen (ADR-0020). Los eventos llevan los datos '
         'mínimos de contacto y nunca datos clínicos.</p>'
         '<h2 id="datos">4.5 Protección de los datos</h2>'
         '<p>En tránsito, la aplicación va por HTTPS (el puerto HTTP redirige) con HSTS, y el canal SOAP lleva WS-Security '
         'con UsernameToken. En reposo, el número de afiliado y la descripción de la historia clínica se cifran por columna '
         'con AES-256-GCM mediante un ' + m("AttributeConverter") + ' de JPA; la clave vive en el credential store de '
         'Elytron y nunca en el repositorio (ADR-0022). En Supabase, todas las tablas tienen Row Level Security, lo que '
         'cierra la API pública que el proveedor expone sobre la base; la aplicación la activa sola al desplegar '
         '(ADR-0018). El número de tarjeta viaja a la pasarela y nunca se guarda: del pago quedan la marca y los últimos '
         'cuatro dígitos.</p>'
         '<h2 id="errores">4.6 Validación, errores y cabeceras</h2>'
         '<p>Los datos de entrada se validan con Bean Validation y en la API con reglas propias (por ejemplo, un JSON sin '
         'campos obligatorios responde 422). Las consultas JPA usan siempre parámetros. Todo error de la API responde en '
         'formato Problem Details (RFC 9457) con un código estable y sin trazas ni nombres de clases o tablas; los SOAP '
         'Fault llevan un código de negocio. Un filtro agrega a cada respuesta las cabeceras de seguridad (HSTS, '
         + m("X-Content-Type-Options") + ', ' + m("X-Frame-Options") + ' y ' + m("Referrer-Policy") + '), y PrimeFaces pone '
         'la política de contenido con un nonce por respuesta.</p>'
         '<h2 id="owasp">4.7 OWASP Top 10: mitigación y verificación</h2>' +
         tabla(5, "Riesgos del OWASP Top 10, su mitigación y la prueba que la verifica", ["Riesgo", "Mitigación", "Casos"],
               owasp, ["22%", None, "15%"],
               "Los casos (CP-xx) están en el documento del QA, con su resultado. Además, OWASP ZAP recorre la web y la "
               "API en cada corrida del pipeline, con 0 alertas altas en la última.") + "</section>")

# 5. Trazabilidad
traza = [
    ["Servicio SOAP con WSDL", m("CoberturaService") + " (simulador del legado de la obra social, en " + m("externos.war") + "), consumido por " + m("CoberturaSoapAdapter") + " con un cliente generado por wsimport"],
    ["Servicio REST", "API propia " + m("/api/v1") + " (" + m("AuthResource") + ", " + m("ProfesionalesResource") + ", " + m("TurnosResource") + ") y clientes REST de la pasarela y la videoconsulta"],
    ["JMS: cola", m("cola.autorizaciones") + " con consumidores en competencia"],
    ["JMS: tópico", m("topico.turnos") + " con tres suscriptores durables (Notificaciones, Auditoría y Facturación)"],
    ["EJB sin estado", "Los servicios de negocio (" + m("ServicioDe*Bean") + "), " + m("ProcesoConfirmacionTurno") + " y los recursos REST"],
    ["EJB con estado", m("ServicioDeTurnosBean") + ": conserva el turno en espera de la conversación de reserva"],
    ["EJB Singleton y temporizadores", m("CatalogoPrestaciones") + ", " + m("LiberadorDeHolds") + ", " + m("VencedorDeDisponibilidad") + ", " + m("ReintentadorDeReintegros")],
    ["Tres o más patrones", "23 patrones aplicados (Tabla 2) y 8 evaluados y descartados (Tabla 3)"],
    ["Seguridad declarativa", m("@RolesAllowed") + " y " + m("@DeclareRoles") + " según la matriz (Tabla 4), más la titularidad programática"],
    ["Transacciones", "CMT: REQUIRED en " + m("ProcesoConfirmacionTurno") + ", MANDATORY en el cobro, REQUIRES_NEW en la bitácora de integraciones, NOT_SUPPORTED en la fachada con estado; envío JMS dentro de la transacción JTA y compensación del cobro"],
]
p.append('<section class="salto"><h1 id="trazabilidad">5. Trazabilidad con el checklist de la cátedra</h1>' +
         tabla(6, "Ítems del checklist y dónde se cumplen", ["Ítem", "Dónde se cumple"], traza, ["24%", None]) + "</section>")

p.append('<section><h1 id="ia">Declaración de uso de IA</h1>'
         '<p>Esta versión final se redactó con Claude Code (Anthropic) a partir del código fuente, de los registros de '
         'decisión y del documento de diseño de octubre, que había escrito el grupo. Cada clase, anotación y comportamiento '
         'que se menciona se comprobó contra el repositorio. El grupo la revisa antes de la entrega.</p></section>')

(AQUI / "diseno-patrones-y-seguridad-final.html").write_text(cabeza + "<body>\n" + "\n".join(p) + "\n</body>\n</html>\n",
                                                             encoding="utf-8", newline="\n")
print("diseno-patrones-y-seguridad-final.html: %d patrones" % len(patrones))
