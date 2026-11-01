# MediConecta

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
  videoconsulta, y expone su propia API `/api/v1` (HTTP Basic, `@RolesAllowed`, DTO,
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
- **Sistemas externos simulados** (`sistemas-externos/`, se despliega como `externos.war`): la obra
  social (SOAP), la pasarela y la videoconsulta (REST), con una consola para tirarlos abajo en vivo.

## Documentación

- [`SETUP.md`](SETUP.md): cómo levantar WildFly, la base en Supabase y desplegar los dos WAR.
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

- Unitarias (JUnit 5): `mvn package` corre 34 pruebas de los patrones (State, Strategy, Chain of
  Responsibility, Circuit Breaker, Retry, Builder, Factory, política de contraseñas, catálogo, selector).
- De punta a punta con navegador real (requieren `pip install playwright` y Google Chrome, con la app
  desplegada):
  - [`pruebas/prueba_entrega4.py`](pruebas/prueba_entrega4.py): 40 verificaciones de esta entrega
    (SOAP, API REST, pago rechazado y aprobado, cola, legado caído, cancelación con reintegro,
    telemedicina, auditoría, temas, roles y alta de usuarios con contraseña temporal).
  - [`pruebas/prueba_e2e.py`](pruebas/prueba_e2e.py): 13 verificaciones del recorrido básico.

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

Cada integrante del equipo puede explicar y defender individualmente cualquier parte del código o
del diseño en la instancia oral, incluidas las decisiones tomadas con asistencia de IA.
