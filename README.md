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
- `ServicioDeNotificaciones`: `NotificacionesSubscriber` (MDB, suscripción durable) genera el email
  al paciente y el aviso en la app al profesional, visibles en `/notificaciones.xhtml`.
- `AuditoriaSubscriber` (MDB, suscripción durable) registra cada evento en `registro_auditoria`.
- **Monitor de eventos** (`/monitor.xhtml`): muestra en vivo, para los turnos del usuario, qué suscriptor
  procesó cada evento y con qué latencia, o cuál lo tiene pendiente (por ejemplo, si se frena un MDB desde
  `jboss-cli`). Se alimenta de `evento_publicado`, que se escribe en la misma transacción que el envío JMS.
- Patrones nuevos: Publish-Subscribe Channel, Durable Subscriber, Event-Driven Consumer, Idempotent
  Receiver (`evento_procesado`), Dead Letter Channel (3 reintentos y DLQ), Builder
  (`EventoTurno.Builder`), Template Method + Factory Method (`ProcesadorNotificacion`) y Transfer Object
  (DTO del monitor).

Las integraciones síncronas (obra social vía SOAP, pagos y telemedicina vía REST), el proceso con
cola punto a punto y la transacción de varios pasos quedan para la Entrega Obligatoria N.º 2.

## Documentación

- [`SETUP.md`](SETUP.md): cómo levantar WildFly, la base en Supabase y desplegar el proyecto
  localmente.
- [`Trabajo practico/MediConecta_Documento_Tecnico_Entrega1.pdf`](<Trabajo practico/MediConecta_Documento_Tecnico_Entrega1.pdf>):
  documento técnico de la Entrega Obligatoria N.º 1 (arquitectura, componentes, patrones,
  seguridad y decisiones de diseño).
- [`Trabajo practico/MediConecta_Diagrama_Capas 1.png`](<Trabajo practico/MediConecta_Diagrama_Capas 1.png>):
  arquitectura en capas planificada para el sistema completo.

- [`docs/diagramas/`](docs/diagramas/): los diez diagramas de la solución (PlantUML + PNG).
- [`docs/documento-maestro/`](docs/documento-maestro/): documento maestro de los diagramas (PDF, formato APA).
- [`docs/presentacion/`](docs/presentacion/): presentación de la Entrega Parcial N.º 2 (PPTX y PDF) y guía
  para la presentación.
- [`pruebas/prueba_e2e.py`](pruebas/prueba_e2e.py): prueba de punta a punta con navegador real
  (14 verificaciones). Requiere `pip install playwright` y Google Chrome.

## Declaración de uso de inteligencia artificial generativa

Conforme a lo exigido en la Sección 9 del TP Integrador, se declara el uso de IA generativa
(Claude, Anthropic) como asistente de productividad durante esta entrega:

- Generación de boilerplate: entidades JPA, DAOs, EJB de negocio y vistas JSF/PrimeFaces.
- Configuración e instalación local de JDK, Maven y WildFly, y su conexión a la base de datos en
  Supabase.
- Diagnóstico y corrección de errores de integración entre Jakarta Security y WildFly 41 al
  combinarlos con un login a medida (detallado en el documento técnico, Sección 6).
- Redacción del documento técnico de esta entrega.

Cada integrante del equipo puede explicar y defender individualmente cualquier parte del código o
del diseño en la instancia oral, incluidas las decisiones tomadas con asistencia de IA.
