# Registros de decisiones de arquitectura (ADR)

Un ADR por cada patrón implementado en el código, más las dos decisiones de seguridad de la entrega del
16/11. Cada uno explica el problema, lo que se decidió, qué alternativas se descartaron y por qué, qué se
gana y qué se pierde, dónde está en el código y con qué prueba se verifica.

La fecha es la de la entrega en la que se tomó la decisión. Los ADR se escribieron para la entrega del
16/11/2026 a partir del código y de los documentos de las entregas anteriores.

| ADR | Decisión | Patrón | Fecha |
|---|---|---|---|
| [ADR-0001](0001-capas-y-dao.md) | Arquitectura en capas con Data Access Object | DAO | 14/09/2026 |
| [ADR-0002](0002-session-facade-turnos.md) | Session Facade con estado para la reserva de turnos | Facade (Session Facade) | 14/09/2026 |
| [ADR-0003](0003-factory-de-usuarios.md) | Factory para crear usuarios según su rol | Factory | 14/09/2026 |
| [ADR-0004](0004-state-del-turno-y-409.md) | State para el ciclo de vida del turno, con 409 ante una transición inválida | State | 09/11/2026 |
| [ADR-0005](0005-strategy-de-copago.md) | Strategy para calcular el copago | Strategy | 09/11/2026 |
| [ADR-0006](0006-chain-of-responsibility-validacion.md) | Chain of Responsibility para validar una reserva | Chain of Responsibility | 09/11/2026 |
| [ADR-0007](0007-adapter-gateway-sistemas-externos.md) | Adapter y Gateway para los sistemas externos | Adapter / Gateway | 09/11/2026 |
| [ADR-0008](0008-circuit-breaker-propio.md) | Circuit Breaker, Retry y Fallback propios frente al legado | Circuit Breaker + Retry + Fallback | 09/11/2026 |
| [ADR-0009](0009-builder-de-eventos.md) | Builder para los eventos del turno | Builder | 19/10/2026 |
| [ADR-0010](0010-template-method-notificaciones.md) | Template Method y Factory Method para las notificaciones | Template Method + Factory Method | 19/10/2026 |
| [ADR-0011](0011-topico-de-eventos.md) | Tópico con suscriptores durables e idempotentes para los eventos del turno | Publish-Subscribe + Durable Subscriber + Idempotent Receiver + Selective Consumer | 19/10/2026 |
| [ADR-0012](0012-cola-de-autorizaciones.md) | Cola punto a punto con consumidores en competencia para las autorizaciones | Point-to-Point + Competing Consumers + Dead Letter Channel + Message Expiration | 09/11/2026 |
| [ADR-0013](0013-transaccion-y-compensacion.md) | Transactional Client y transacción compensatoria en la confirmación | Transactional Client + compensación | 09/11/2026 |
| [ADR-0014](0014-intercepting-filter-auditoria.md) | Intercepting Filter para auditar el acceso a la historia clínica | Intercepting Filter | 09/11/2026 |
| [ADR-0015](0015-singleton-catalogo.md) | Singleton para el catálogo de prestaciones | Singleton | 09/11/2026 |
| [ADR-0016](0016-transfer-object-api.md) | Transfer Object para la API REST | Transfer Object (DTO) | 09/11/2026 |
| [ADR-0017](0017-jwt-en-la-api.md) | JWT para autenticar la API REST | Seguridad (token sin estado) | 16/11/2026 |
| [ADR-0018](0018-rls-en-supabase.md) | Row Level Security en las tablas de Supabase | Seguridad (defensa en profundidad) | 16/11/2026 |
| [ADR-0019](0019-revocar-el-token-al-cerrar-sesion.md) | Revocar el JWT al cerrar sesión | Seguridad (lista de revocación) | 19/11/2026 |
| [ADR-0020](0020-permisos-por-destino-en-el-broker.md) | Permisos por destino en el broker, sin el rol guest | Seguridad (mínimo privilegio en la mensajería) | 19/11/2026 |
| [ADR-0021](0021-pbkdf2-con-600000-iteraciones.md) | PBKDF2 con 600.000 iteraciones, y por qué no bcrypt ni Argon2 | Seguridad (hash adaptativo) | 19/11/2026 |
| [ADR-0022](0022-cifrado-de-columnas-con-aes-gcm.md) | Cifrado de columnas con AES-GCM y la clave en el credential store de Elytron | Seguridad (cifrado en reposo) | 19/11/2026 |
| [ADR-0023](0023-ficha-del-paciente-con-decorator.md) | Ficha del paciente con el Decorator PacienteEnmascarado | Decorator (GoF, CDI) | 19/11/2026 |
| [ADR-0024](0024-reintegros-pendientes.md) | Reintegros pendientes, y la falla de demostración fuera de la aplicación | Compensación con reintentos | 19/11/2026 |

Las rutas de código son relativas a `src/main/java/ar/edu/mediconecta/`. Las pruebas unitarias están en
`src/test/java` y las de navegador en `pruebas/`.
