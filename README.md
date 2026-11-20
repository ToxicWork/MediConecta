# MediConecta

[![CI](https://github.com/ToxicWork/MediConecta/actions/workflows/ci.yml/badge.svg)](https://github.com/ToxicWork/MediConecta/actions/workflows/ci.yml)

Plataforma que conecta pacientes con profesionales de salud independientes y pequeñas clínicas
para turnos presenciales y telemedicina, con facturación a obras sociales y prepagas.

TP Integrador, Desarrollo de Aplicaciones II (comisión Lunes TT).

## Estado actual

**Entrega Obligatoria N.º 1** (Unidades I-II-III): tres componentes completos, implementados en
capas y desplegados sobre Jakarta EE 10 (JSF + PrimeFaces, EJB, JPA/Hibernate), WildFly y
PostgreSQL (Supabase).

- `ServicioDeUsuarios`, EJB `@Stateless`. Registro y autenticación de pacientes y profesionales.
- `ServicioDeTurnos`, EJB `@Stateful` con `@StatefulTimeout`. Publicación de disponibilidad,
  reserva con hold temporal (5 min) y confirmación de turnos.
- `ServicioDeHistoriaClinica`, EJB `@Stateless` con `@RolesAllowed`. Carga y consulta de
  diagnósticos y recetas.

Patrones aplicados: DAO, Facade y Factory. Seguridad declarativa con
`@DatabaseIdentityStoreDefinition` (Jakarta Security) y `@RolesAllowed`.

**Entrega Parcial N.º 2** (Unidades IV-V): primer proceso de negocio con mensajería asincrónica
real, sobre el broker ActiveMQ Artemis embebido en WildFly (perfil `standalone-full.xml`).

- Al confirmar o cancelar un turno, `ServicioDeTurnos` publica un `EventoTurno` (JSON) en
  `topico.turnos`. El envío JMS se enlista en la misma transacción JTA que actualiza el turno
  (Transactional Client): si hay rollback, el evento no sale.
- `ServicioDeNotificaciones` y `AuditoriaSubscriber`: MDB con suscripción durable.
- Patrones nuevos: Publish-Subscribe Channel, Durable Subscriber, Event-Driven Consumer, Idempotent
  Receiver, Dead Letter Channel, Builder, Template Method + Factory Method y Transfer Object.

**Entrega Obligatoria N.º 2 — 2.º Parcial** (Unidades IV-V-VI, 09/11; el anexo la llama
"Entrega 4"): integración sincrónica SOAP y REST, mensajería completa y transacciones.

- **SOAP con el sistema legado de la obra social** (`CoberturaService`, con WSDL): validación de
  cobertura y autorización de prestaciones. Cliente generado con wsimport desde el contrato
  (`src/main/resources/wsdl`), WS-Security UsernameToken, Adapter/Gateway y resiliencia propia:
  timeout, reintento, Circuit Breaker y fallback con caché.
- **REST**: MediConecta consume la pasarela de pagos (Bearer + `Idempotency-Key`) y el proveedor de
  videoconsulta, y expone su propia API `/api/v1` (HTTP Basic en esa entrega, JWT desde el 16/11; `@RolesAllowed`, DTO,
  `ExceptionMapper`).
- **Mensajería completa**: cola punto a punto `cola.autorizaciones` (Competing Consumers, TTL 24 h,
  reintentos con espera creciente y DLQ) y un tercer suscriptor del tópico, Facturación, con
  selector (Selective Consumer): reclamos a obras sociales y reintegros por cancelación.
- **Transacciones declarativas**: `ProcesoConfirmacionTurno.confirmar` (REQUIRED) valida, consulta
  la cobertura por SOAP, cobra por REST, confirma, encola la autorización y publica el evento en una
  sola transacción JTA; si falla a mitad de camino, la base y los mensajes se revierten y el cobro
  se devuelve con una transacción compensatoria. También se usan MANDATORY, SUPPORTS,
  NOT_SUPPORTED y REQUIRES_NEW, cada uno justificado.
- Componentes nuevos: `ServicioDeObrasSociales`, `ServicioDePagos`, `ServicioDeTelemedicina`,
  `ServicioDeFacturacion` y `ServicioDeAgenda` (los ocho componentes del dominio quedan implementados).
- Patrones nuevos: Adapter/Gateway, Circuit Breaker + Retry + Fallback, Strategy (copago), Chain of
  Responsibility (validación de reserva), State (turno), Intercepting Filter (`@Auditado`),
  Singleton (`CatalogoPrestaciones`), Point-to-Point, Competing Consumers, Selective Consumer,
  Message Expiration y transacción compensatoria.
- Seguridad: titularidad de la historia clínica en el servicio (con registro de accesos visible para
  el paciente), `@RunAs("SISTEMA")` en los MDB, Bean Validation de los datos de pago y el número de
  tarjeta nunca se guarda.
- **Temas visuales** estándar, claro y oscuro (opcional del anexo), alternables en tiempo real.
- Pantallas nuevas: Pagos y Telemedicina.
- **Ajustes posteriores (noviembre 2026):**
  - rol **Administrativo**: ve Pagos (todos los cobros y reclamos; el profesional deja de tener esa
    solapa) y **Usuarios** (alta de pacientes, profesionales y administrativos con contraseña temporal);
  - **registro aparte y sólo de pacientes** (`registro.xhtml`, enlace "¿No tenés cuenta? Registrate");
  - **Mi perfil** para todos (datos de sólo lectura y cambio de contraseña) y **cambio obligatorio** de
    la contraseña temporal en el primer ingreso;
  - **administrador por defecto** (`admin@mediconecta.test`) que se crea al desplegar;
  - calendario de fecha y hora para publicar disponibilidad, **vencimiento automático** de la
    disponibilidad no reservada (timer del contenedor) y cámara que se activa y desactiva en la
    videoconsulta.

  Requiere correr `sql/01-rol-administrativo.sql` una vez en la base (ver `SETUP.md`).
**2.ª Entrega Parcial: el núcleo funcionando** (16/11): seguridad base, patrón State por HTTP y decisiones
documentadas.

- **JWT en la API REST**: `POST /api/v1/auth/login` entrega un token firmado (HS256, 30 minutos) y el resto
  de los pedidos lo envían como `Authorization: Bearer`. Reemplaza a HTTP Basic. El control por rol sigue en
  los `@RolesAllowed` de los EJB. La web conserva la sesión del contenedor.
- **Patrón State con 409**: `POST /api/v1/turnos/{id}/cancelacion`. Cancelar un turno que ya está cancelado
  o que todavía está en espera es una transición inválida y la API responde `409 Conflict`
  (`transicion_invalida`). Los servicios no preguntan por el estado: lo decide el State.
- **ADR**: un registro de decisión por cada patrón implementado, en [`docs/adr/`](docs/adr/).
- **Row Level Security** en todas las tablas de Supabase (`sql/02-habilitar-rls.sql`).
- Cookie de sesión `HttpOnly` y sin id de sesión en la URL.
- 13 pruebas unitarias nuevas (JWT, códigos HTTP y la matriz completa de transiciones inválidas) y verificaciones nuevas en las pruebas de navegador.

**Tarjetas de seguridad y cancelación (14/11)**: las nueve tarjetas de "POR HACER" del tablero. Resuelven los
defectos D-01, D-02, D-04, D-07 y D-09 del informe del 16/11.

- **SEC-W01**: cinco contraseñas incorrectas seguidas bloquean la cuenta 15 minutos (web y API, con el mismo
  mensaje genérico); el administrativo la puede desbloquear desde Usuarios.
- **SEC-W04**: toda la app por **HTTPS** (`https://localhost:8443/mediconecta/`; `http` redirige) y cookie de
  sesión con `HttpOnly`, `Secure` y `SameSite=Lax`.
- **SEC-W06, SEC-I01, SEC-I02 y SEC-D01**: el código ya cumplía; se agregaron las pruebas que lo demuestran
  (inyección SQL, eventos sin datos clínicos, Dead Letter Channel con la pasarela caída y reproceso, historia
  clínica sólo con turno confirmado). En la historia clínica, además, el servidor valida el turno de cada entrada.
- **SEC-D02**: los emails se escriben enmascarados en el log (`ju***@mediconecta.test`); el SQL de Hibernate se
  apagó para que el log muestre lo importante.
- **HU-09**: vuelve el **Monitor de eventos** (paciente y profesional), mínimo: sólo los avisos de sus turnos,
  con el estado y la latencia de cada suscriptor, de a 10 por página.
- **Cancelación por el profesional y el administrativo**: botón Cancelar en la agenda del profesional y pantalla
  **Turnos** del administrativo (de a tandas, con filtro por profesional). El turno registra quién lo canceló,
  los avisos lo dicen y el horario no se vuelve a ofrecer (sí cuando cancela el paciente). También por la API.

**Presentación del 16/11 (15/11)**: revisión contra la consigna de la Entrega Obligatoria N.º 2.

- **Falla a mitad del flujo crítico, en vivo**: el administrativo la arma desde **Pagos** («Armar falla antes del
  commit», `ControlDeDemostracion` con `@RolesAllowed`). La próxima confirmación falla después de cobrar y antes
  del commit: la base y los mensajes se revierten y el cobro se reembolsa (escenario F de `prueba_entrega4.py`).
  Desde el 18/11 está apagada salvo que el servidor se levante con `-Dmediconecta.demo.fallas=true` (o, en caliente,
  `/system-property=mediconecta.demo.fallas:add(value=true)` en jboss-cli): el informe del núcleo prometió retirarla
  de la entrega final. **Se retiró el 19/11 (D-42)**: ahora la falla la provoca la prueba de integración
  `CompensacionIT` (Arquillian), sin ningún gancho en el código de la aplicación.
- **Pago inexistente en la pasarela**: un 404 al reintegrar ya no cuenta como caída para el Circuit Breaker ni se
  reintenta; queda en el log para conciliación manual.
- Guion y diapositivas nuevos (`docs/entrega-nucleo/`, con `generar_presentacion.py`), informe actualizado y
  documento técnico de la Obligatoria N.º 2 llevado a la extensión de la consigna.

- **Sistemas externos simulados** (`sistemas-externos/`, se despliega como `externos.war`): la obra
  social (SOAP), la pasarela y la videoconsulta (REST), con una consola para tirarlos abajo en vivo.

**Promesas del diseño completadas (19/11)**:

- **Decorator `PacienteEnmascarado`** (diseño 4.8, [ADR-0023](docs/adr/0023-ficha-del-paciente-con-decorator.md)).
  La ficha del paciente se abre desde Usuarios (administrativo), la Agenda y la Historia clínica (profesional). Sólo
  el profesional con un turno confirmado con el paciente ve los datos clínicos; el administrativo ve la identificación
  y la cobertura.
- **Cifrado de columnas con AES-GCM** (diseño 8.6, [ADR-0022](docs/adr/0022-cifrado-de-columnas-con-aes-gcm.md)). El
  n.º de afiliado y las entradas de la historia clínica se guardan cifrados. La clave está en el credential store de
  Elytron, nunca en el código ni en la configuración en claro; se configura con `wildfly/configurar_cifrado.py`
  (SETUP, sección 2.4). Sin clave la aplicación no despliega.
- Las entradas de la historia clínica ya no se guardan vacías ni de más de 2000 caracteres, y el n.º de afiliado
  tiene hasta 40.
- **Reintegros que la pasarela no pudo hacer** (D-06 y D-41, [ADR-0024](docs/adr/0024-reintegros-pendientes.md)): si la
  compensación no puede devolver el cobro porque la pasarela no responde, el reintegro queda pendiente y se reintenta
  solo cada minuto, con espera creciente. Si la pasarela no conoce el pago, queda para conciliación manual. El
  administrativo los ve en **Pagos** y los puede reintentar o marcar como conciliados. Antes quedaban sólo en el log.
- **OWASP Dependency-Check** (prometido en el diseño): un trabajo del pipeline analiza las dependencias del WAR contra la
  base de la NVD y falla con una CVE de CVSS 7 o más sin una supresión justificada
  (`dependency-check-supresiones.xml`). Complementa a Trivy. Localmente: `mvn -P dependencias verify -DskipTests`.
- **La falla de demostración salió de la aplicación** (D-42): la reemplaza `CompensacionIT`, una prueba de
  integración que hace fallar la confirmación real antes del commit.

## Documentación

- [`SETUP.md`](SETUP.md): cómo levantar WildFly, la base en Supabase y desplegar los dos WAR.
- [`docs/entrega-nucleo/MediConecta_Entrega_Nucleo_16-11.pdf`](docs/entrega-nucleo/MediConecta_Entrega_Nucleo_16-11.pdf):
  informe de la entrega del 16/11 (JWT, State con 409, ADR, pruebas con su evidencia, trazabilidad de las
  historias de usuario, defectos conocidos y cambios respecto del diseño).
- [`docs/adr/`](docs/adr/): registros de decisiones de arquitectura, uno por patrón.
- Tablero del equipo (Trello): <https://trello.com/b/7c84ZN3J/mediconecta-grupo-04>.
- [`docs/documento-tecnico/MediConecta_Documento_Tecnico_Entrega4.pdf`](docs/documento-tecnico/MediConecta_Documento_Tecnico_Entrega4.pdf):
  documento técnico de la Entrega Obligatoria N.º 2 (integraciones con sus diagramas de secuencia,
  mensajería, transacciones, patrones, seguridad, temas y pruebas).
- [`docs/guia-demo-entrega4.md`](docs/guia-demo-entrega4.md): guion de la demo en vivo y preguntas
  probables de la defensa oral.
- [`docs/api/README.md`](docs/api/README.md): contratos SOAP y REST con ejemplos para probarlos.
- [`docs/manual/MediConecta_Manual_de_Usuario.pdf`](docs/manual/MediConecta_Manual_de_Usuario.pdf):
  manual de usuario (versión 0.4: pagos, teleconsultas, temas, Mi perfil y el rol administrativo).
- [`docs/diagramas/`](docs/diagramas/): diagramas de la solución (PlantUML + PNG), incluidos los de
  secuencia de cada integración.
- [`docs/documento-tecnico/MediConecta_Documento_Tecnico_Entrega2.pdf`](docs/documento-tecnico/MediConecta_Documento_Tecnico_Entrega2.pdf),
  [`docs/documento-maestro/`](docs/documento-maestro/) y [`docs/presentacion/`](docs/presentacion/):
  documentación de la Entrega Parcial N.º 2.
- [`Trabajo practico/MediConecta_Documento_Tecnico_Entrega1.pdf`](<Trabajo practico/MediConecta_Documento_Tecnico_Entrega1.pdf>)
  y [`Diseno_Patrones_y_Seguridad_MediConecta_Grupo4.pdf`](Diseno_Patrones_y_Seguridad_MediConecta_Grupo4.pdf):
  documento de la Entrega Obligatoria N.º 1 y diseño de patrones y seguridad.

## Pruebas

- Unitarias (JUnit 5): `mvn verify` corre las pruebas (State, Strategy, Chain of Responsibility, Circuit
  Breaker, Retry, Builder, Factory, política de contraseñas, catálogo, JWT, códigos HTTP de la API, bloqueo
  por intentos, eventos sin datos clínicos, enmascarado de emails y cancelación por rol) con la cobertura de
  JaCoCo; `mvn test-compile org.pitest:pitest-maven:mutationCoverage` corre las de mutación (PIT) sobre el
  dominio.
- Integración continua ([`.github/workflows/ci.yml`](.github/workflows/ci.yml), en cada push y pull request):
  compila los dos proyectos, corre las pruebas unitarias con los umbrales de cobertura y de mutación, repite la
  suite tres veces para detectar pruebas inestables, busca secretos en todo el historial (gitleaks) y
  vulnerabilidades en las dependencias (Trivy). El análisis estático de SonarCloud corre cuando el repositorio
  tiene el secreto `SONAR_TOKEN`. Los hallazgos de gitleaks revisados, ninguno un secreto, están con su motivo en
  [`.gitleaksignore`](.gitleaksignore).
  Las pruebas de punta a punta también corren en el pipeline, sin contenedores. El trabajo levanta la aplicación
  completa en el runner:
  - WildFly 41, bajado de Maven Central con verificación de checksum y configurado con los scripts de `wildfly/`;
  - el PostgreSQL y el Google Chrome que ya trae la imagen de Ubuntu;
  - los usuarios de demo, creados con
    [`pruebas/sembrar_usuarios_demo.py`](pruebas/sembrar_usuarios_demo.py);
  - las contraseñas de cada corrida, generadas al azar y enmascaradas.

  Las suites corren en dos grupos paralelos, así el pipeline completo tarda unos 8 minutos. El resultado de cada
  suite queda como aviso en la página del run, y el `server.log` como artefacto.
- De punta a punta con navegador real (requieren `pip install playwright` y Google Chrome, o Edge con
  `NAVEGADOR=msedge`, con la app desplegada; ver `SETUP.md`):
  - [`pruebas/prueba_seguridad.py`](pruebas/prueba_seguridad.py): las pruebas de seguridad del diseño (SEC-W01,
    W04, W06, I02, D01 y D02). Necesita `WILDFLY_HOME`.
  - [`pruebas/prueba_monitor.py`](pruebas/prueba_monitor.py): Monitor de eventos (HU-09), con un suscriptor
    frenado y recuperado y la paginación. Necesita `WILDFLY_HOME`.
  - [`pruebas/prueba_cancelacion.py`](pruebas/prueba_cancelacion.py): cancelación por el profesional, el
    administrativo, el paciente y la API.
  - [`pruebas/prueba_entrega4.py`](pruebas/prueba_entrega4.py): verificaciones de punta a punta
    (SOAP, API REST con JWT, transición inválida con 409, pago rechazado y aprobado, cola, falla a mitad del flujo con
    rollback y reembolso, legado caído, cancelación con reintegro,
    telemedicina, auditoría, temas, roles y alta de usuarios con contraseña temporal).
  - [`pruebas/prueba_e2e.py`](pruebas/prueba_e2e.py): 13 verificaciones del recorrido básico.
  - [`pruebas/prueba_defectos.py`](pruebas/prueba_defectos.py): un escenario por cada defecto de la prueba
    exploratoria y de las regresiones (D-13 a D-40), incluidos el doble clic, dos usuarios sobre el mismo turno
    y otra transacción que modifica el turno mientras el paciente cancela (este último necesita `WILDFLY_HOME`).
  - [`pruebas/prueba_hold_vence.py`](pruebas/prueba_hold_vence.py): el hold de 5 minutos vence solo (tarda
    unos 6 minutos).
  - [`pruebas/demo_api_jwt_409.py`](pruebas/demo_api_jwt_409.py): guion por consola del login con JWT, los
    401 y 403 y el 409 del patrón State (sólo necesita Python).
  - [`pruebas/prueba_broker.py`](pruebas/prueba_broker.py): con el broker caído y vuelto a levantar no se
    pierde ningún mensaje.
    1. Con la cola de autorizaciones en pausa, el paciente confirma un turno.
    2. Se reinicia el servidor con `jboss-cli`, y con él el broker.
    3. Al volver, la autorización se procesa sola y no pasa por la DLQ.

    Necesita `WILDFLY_HOME` y reinicia el servidor.
  - [`pruebas/prueba_permisos_broker.py`](pruebas/prueba_permisos_broker.py): permisos por destino en el broker
    ([ADR-0020](docs/adr/0020-permisos-por-destino-en-el-broker.md)). Un cliente JMS de afuera
    ([`ClienteJmsExterno.java`](pruebas/ClienteJmsExterno.java)) prueba que sin usuario no entra y que con un
    usuario del rol `guest` no puede publicar ni leer en ningún destino. También verifica que la aplicación sigue
    conectada al broker. Necesita `WILDFLY_HOME`: da de alta ese usuario con `add-user`.
  - [`pruebas/prueba_datos_protegidos.py`](pruebas/prueba_datos_protegidos.py): la ficha del paciente (Decorator) vista
    por la profesional tratante y por el administrativo, y el cifrado AES-GCM mirado directamente en la base
    ([`VerificarCifrado.java`](pruebas/VerificarCifrado.java)): el dato está cifrado, el texto no aparece y no quedan
    valores sin cifrar. Necesita `WILDFLY_HOME` para la parte de la base.
  - [`pruebas/prueba_contrato_wsdl.py`](pruebas/prueba_contrato_wsdl.py): el WSDL que publica el legado de la obra
    social contra el contrato del que se genera el cliente (`src/main/resources/wsdl/CoberturaService.wsdl`):
    operaciones, mensajes, tipos campo por campo, enumeraciones y binding. Verifica también que un contrato alterado a
    propósito se detecte.
  - Pruebas de integración con Arquillian ([`src/it/java`](src/it/java), `mvn -P integracion verify`): se despliegan en
    el WildFly levantado y usan su PostgreSQL y su broker, sin mocks. `RepositoriosIT` prueba las consultas de los DAO,
    el cifrado del afiliado en la columna y la idempotencia por la clave de la base; `ConsumidoresDeMensajesIT`
    publica un evento real en el tópico y verifica que los MDB de la aplicación lo procesen una sola vez aunque llegue
    dos veces. Las corre el pipeline en el grupo 3.
  - Pruebas de la API con **REST Assured** (`ApiRestAssuredIT`, en el mismo perfil de integración): cada endpoint
    del OpenAPI con al menos un caso de éxito y uno de error, pedidos desde afuera por HTTPS y con el JWT. Un primer
    paso adentro del servidor deja un paciente de prueba con un turno confirmado. Encontraron el D-48: la
    disponibilidad de un profesional que no existe respondía 200 con una lista vacía; ahora responde 404.
  - [`pruebas/manual/`](pruebas/manual): los scripts de apoyo de las pruebas manuales del 19/11 (CP-90 y CP-92). No son
    suites: registran lo observado para que una persona lo revise.
  - [`pruebas/prueba_inyeccion.py`](pruebas/prueba_inyeccion.py): SQL, XSS y expresiones EL en todos los campos
    de texto. Se verifica en cada pantalla donde se muestran: que se vean literales, que no se ejecute nada y que
    no haya errores 500 ni rastros de la base.
  - [`pruebas/prueba_contrato_api.py`](pruebas/prueba_contrato_api.py): pruebas de contrato de la API REST contra
    su OpenAPI, [`docs/api/openapi.json`](docs/api/openapi.json). Provoca cada respuesta documentada y la compara
    con el contrato: código, tipo de contenido, cabeceras y cuerpo, y al final la cobertura del contrato.
  - [`pruebas/prueba_responsive.py`](pruebas/prueba_responsive.py): cada pantalla de cada rol a 360, 768 y 1280 px.
    Verifica que no haya scroll horizontal y deja una captura de cada una.
  - Pruebas no funcionales. Las corre el pipeline, cada una con su propia aplicación levantada en el runner:
    - [`pruebas/carga/carga_api.js`](pruebas/carga/carga_api.js) (k6): 50 usuarios durante 5 minutos sobre la búsqueda
      de disponibilidad, "mis turnos" y el detalle de un turno. Exige p95 menor a 500 ms y menos de 1 % de errores en
      cada uno, y deja el reporte en el resumen del run. Los datos los prepara
      [`pruebas/carga/preparar_datos.py`](pruebas/carga/preparar_datos.py).
    - [`pruebas/seguridad/zap-plan.yaml`](pruebas/seguridad/zap-plan.yaml) (OWASP ZAP): escaneo pasivo y activo
      autenticado de la web y de la API (con su OpenAPI). Falla si hay alertas High; el reporte queda en el artefacto
      del run.
    - [`pruebas/prueba_accesibilidad.py`](pruebas/prueba_accesibilidad.py): axe-core en cada pantalla de cada rol a 360,
      768 y 1280 px, sin violaciones críticas de WCAG 2.2 AA. Fuera del pipeline puede sumar Lighthouse en las
      pantallas principales (90 o más de accesibilidad).
  - [`pruebas/sembrar_usuarios_demo.py`](pruebas/sembrar_usuarios_demo.py): no es una prueba. Crea los usuarios
    de demo en una base vacía a través de la propia aplicación, igual que el pipeline (ver `SETUP.md`).

## Declaración de uso de inteligencia artificial generativa

Conforme a lo exigido en la Sección 9 del TP Integrador, se declara el uso de IA generativa
(Claude, Anthropic) como asistente de productividad:

- Generación de boilerplate: entidades JPA, DAOs, EJB de negocio y vistas JSF/PrimeFaces.
- Configuración e instalación local de JDK, Maven y WildFly, su conexión a la base de datos en
  Supabase y la configuración del broker.
- Diagnóstico y corrección de errores de integración entre Jakarta Security y WildFly 41.
- Entrega Obligatoria N.º 2: código base de las integraciones SOAP y REST, de los simuladores de los
  sistemas externos, de la cola de autorizaciones, del flujo transaccional y de los temas visuales;
  pruebas unitarias y de navegador; diagramas; borradores del documento técnico, del manual y de la
  guía de la demo.
- Entrega del 16/11: implementación del JWT, del endpoint de cancelación con 409 y de sus pruebas;
  redacción de los ADR a partir del código; habilitación de RLS en la base; ejecución de las pruebas y
  redacción del informe de la entrega. Todo resultado informado sale de una ejecución real, con su fecha.

Cada integrante del equipo puede explicar y defender individualmente cualquier parte del código o
del diseño en la instancia oral, incluidas las decisiones tomadas con asistencia de IA.
