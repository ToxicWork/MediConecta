# Guía de la demo y la defensa · Entrega Obligatoria N.º 2 (09/11)

> **Actualizada en noviembre de 2026.** Se retiraron las pantallas *Monitor de eventos* e *Integraciones*:
> lo que antes se mostraba ahí ahora se ve en la Agenda ("Autorización en trámite" → "Autorizada"), en las
> Notificaciones, en Pagos (administrativo), en la consola de sistemas externos y en el log del servidor.
> Se agregaron el rol Administrativo, el registro aparte, "Mi perfil" y el cambio de contraseña (bloque 7).

La consigna pide, para la defensa (20 a 25 minutos):

1. demo end-to-end de al menos un flujo de negocio completo que cruce componentes internos, mensajería
   y servicios web;
2. explicar por qué cada integración externa se resolvió de forma sincrónica o asincrónica;
3. explicar el comportamiento transaccional ante una falla a mitad del flujo crítico.

El anexo agrega que todo debe mostrarse **en vivo** y que cada integrante tiene que poder responder
sobre cualquier parte. Esta guía ordena la demo en bloques cortos, cada uno con lo que hay que mostrar,
lo que hay que decir y lo que se espera ver.

## Antes de empezar (10 minutos antes)

- [ ] Supabase activo (el plan gratuito se pausa tras una semana sin uso: si el log dice
      `tenant/user ... not found`, reanudarlo desde el panel).
- [ ] WildFly levantado con `wildfly/iniciar-servidor.bat` (o `.sh`): perfil `standalone-full`.
- [ ] Desplegados `mediconecta.war` y `externos.war` (ver `SETUP.md`). En el log: `Deployed "mediconecta.war"`
      y `WSDL published to: .../CoberturaService.wsdl`.
- [ ] Consola de sistemas externos (<http://localhost:8080/externos/>) con todo **En línea** y
      autorizaciones en **Aprobar**.
- [ ] Cuatro pestañas (usar ventanas privadas distintas para no mezclar sesiones): paciente
      (`juan.perez@mediconecta.test`), profesional (`ana.lopez@mediconecta.test`), administrativo
      (`carla.admin@mediconecta.test`), los tres con `demo1234`, y la consola de externos.
- [ ] La profesional publicó dos horarios futuros con el calendario: uno **Presencial** y uno **Telemedicina**.
- [ ] El log del servidor a la vista (ahí se ven los consumidores de la cola y los timers).
- [ ] Opcional: correr `python pruebas/prueba_entrega4.py` (40 verificaciones) como prueba de humo.

## Bloque 1 · Arquitectura y clasificación (3 min)

Mostrar la Figura 1 del documento técnico (`docs/diagramas/01-integracion-soa.png`).

Decir: la regla es **si quien inicia necesita la respuesta para seguir, es sincrónica**; si es una
consecuencia que puede esperar o le interesa a varios, es asincrónica. Dentro de las sincrónicas, el
protocolo lo pone el tercero: la obra social es un legado que sólo expone SOAP; la pasarela y el video
son partners modernos con REST. Y MediConecta expone su propia API REST para partners.

| Integración | Tipo | Por qué |
|---|---|---|
| Validar cobertura | SOAP sincrónico | el paciente necesita saber cuánto paga antes de confirmar |
| Cobrar | REST sincrónico | sin cobro aprobado no se confirma |
| Videoconsulta | REST sincrónico | el enlace se necesita para entrar |
| Autorizar prestación | cola | una sola vez, el legado puede tardar o caerse, nadie espera |
| Eventos del turno | tópico | varios interesados (notificaciones, auditoría, facturación) que no se conocen |

## Bloque 2 · Flujo end-to-end feliz (5 min)

1. **Paciente → Agenda**: buscar a la Dra. Ana López → **Reservar** el horario presencial.
   - Ver el recuadro naranja: **cobertura consultada por SOAP** ("Consultada en línea al sistema de la obra
     social (SOAP) · trx …"), y la **liquidación**: $32.000, OSDE cubre $22.400, a cargo $9.600
     (estrategia de copago porcentual).
2. Mostrar la consola de externos: en "Llamadas recibidas" aparece `validarCobertura OSDE 210 · 420101`.
3. Pagar con `4000 0000 0000 0002` → **"Pago rechazado: fondos insuficientes"** y el turno sigue en espera.
4. Pagar con `4111 1111 1111 1111` → **"Turno confirmado… Se cobraron $ 9600.00…"**.
5. **Mis turnos**: el turno aparece con **"Autorización en trámite"** y, en unos segundos, **"Autorizada"**
   (el código AUT-… aparece al pasar el mouse). La resolvió `AutorizacionConsumer` desde
   **cola.autorizaciones**; en el log se ve `[cola.autorizaciones] Solicitud … resuelta en el intento 1` con el
   hilo del consumidor.
6. **Notificaciones** del paciente: la confirmación y la autorización (las publicó el tópico).
7. **Administrativo → Pagos**: el cobro de $9.600 y el **reclamo a OSDE por $22.400** con el código de
   autorización, que generó Facturación al consumir TURNO_AUTORIZADO (Selective Consumer: no recibe
   TURNO_CONFIRMADO).

Decir: un solo clic del paciente cruzó cuatro componentes internos, un SOAP, un REST, una cola y un tópico.

## Bloque 3 · Falla a mitad del flujo crítico (5 min) — el corazón de la defensa

La falla de demostración se armaba desde la pantalla Integraciones, que se retiró. Este bloque se explica
con la Figura del diagrama 13 (`13-secuencia-falla-compensacion.png`) y se muestran en vivo los casos que
sí se pueden provocar:

1. **Pago rechazado** (bloque 2, paso 3): la transacción no llega a confirmar; el turno **sigue en espera**
   (el bean `@Stateful` no se destruyó) y el paciente puede reintentar.
2. **Pasarela caída** (consola de externos → Pasarela → Caído): al confirmar aparece *"No pudimos
   comunicarnos con la pasarela de pagos. Tu turno sigue reservado…"*. El turno **no se confirma sin cobrar**
   (no hay fallback para el cobro: sería regalar la consulta) y la transacción no deja nada a medias.
3. Explicar con el diagrama qué pasa si la falla ocurre **después de cobrar**: la base y el broker se revierten
   solos porque participan de la misma transacción JTA (el envío JMS es XA: Transactional Client); la
   **pasarela REST no es transaccional**, así que el cobro se compensa con un reembolso desde una
   `Synchronization` registrada en la transacción (`CompensacionDePago.afterCompletion`); la **consulta SOAP**
   no tiene efectos, no hay nada que compensar. La bitácora de llamadas se escribe con **REQUIRES_NEW** para
   que sobreviva al rollback.

## Bloque 4 · El legado se cae (4 min) — resiliencia (desafío opcional)

1. Consola de externos: Obra social → **Caído**.
2. Paciente: reservar → la cotización dice *"El sistema de la obra social no respondió: se usó la última
   validación vigente (caché)"* (o "cobertura pendiente" si no había caché).
3. Confirmar: funciona igual. En **Mis turnos** el turno queda con **"Autorización en trámite"**; en el log se
   ven los intentos fallidos de la cola (`[cola.autorizaciones] Intento N …`).
4. Consola: Obra social → **En línea**. En menos de un minuto la autorización pasa sola a **Autorizada**.

Decir: timeout (2 s / 3 s) → un reintento (sólo en la consulta, que es idempotente) → Circuit Breaker
(3 fallas, 30 s abierto) → fallback con caché. El circuito evita colgar hilos esperando timeouts. La
autorización no se pierde: el consumidor hace `setRollbackOnly` y el broker reintenta con espera creciente
(10, 20, 40, 60 s; 8 intentos y DLQ). Con el modo **Lento** se ve el timeout: corta a los 3 s.

## Bloque 5 · Cancelación, telemedicina y API (3 min)

- Paciente cancela el turno pagado → "El reintegro… se procesa en unos segundos". En Pagos pasa a
  **Reembolsado**; en Pagos del administrativo el reclamo pasa a **Anulado**. Lo hizo Facturación al consumir
  TURNO_CANCELADO, **después del commit** de la cancelación.
- Teleconsulta: la profesional entra a **Telemedicina → Iniciar videoconsulta** (POST REST al proveedor);
  el paciente ve **Unirme a la consulta** sin recargar. En la sala, **Activar cámara / Desactivar cámara**
  (al desactivarla se libera la cámara y vuelve el avatar).
- API: en una terminal,
  `curl -i http://localhost:8080/mediconecta/api/v1/turnos` → 401;
  `curl -u juan.perez@mediconecta.test:demo1234 http://localhost:8080/mediconecta/api/v1/turnos` → JSON;
  `curl -i -u carla.admin@mediconecta.test:demo1234 http://localhost:8080/mediconecta/api/v1/turnos` → 403.

## Bloque 6 · Temas (30 s)

Cambiar **Estándar → Claro → Oscuro** desde la barra superior en cualquier pantalla: el cambio es
instantáneo, sin recargar, y se mantiene al navegar.

## Bloque 7 · Roles, usuarios y agenda (4 min)

1. **Registro**: en el login, "¿No tenés cuenta? **Registrate**" lleva a una pantalla aparte que sólo crea
   **pacientes** (no hay selector de rol, y el servicio `registrarPaciente` no recibe el rol: no se puede
   forzar armando el pedido a mano).
2. **Administrativo → Usuarios**: dar de alta un profesional con matrícula, especialidad y una contraseña
   temporal. En la lista aparece con contraseña **Temporal**.
3. Ingresar con ese profesional: va directo a **Mi perfil** con el aviso "Elegí tu contraseña para continuar";
   el menú sólo ofrece Mi perfil y cualquier otra URL lo devuelve ahí. Tras cambiarla, entra a su Agenda.
4. **Mi perfil** (cualquier rol): datos de sólo lectura y cambio de contraseña (pide la actual).
5. **Agenda del profesional**: publicar disponibilidad con el **calendario** (día y hora). Los horarios que
   nadie reservó **se vencen solos**: en el log, `Disponibilidad vencida: se eliminaron N horario(s)…`.

Decir: la seguridad declarativa está en el servicio, operación por operación (`@PermitAll` para el registro,
`@RolesAllowed("ADMINISTRATIVO")` para dar de alta y listar usuarios, los tres roles para cambiar la propia
contraseña); las pantallas sólo acompañan. El primer administrador (`admin@mediconecta.test`) lo crea un
`@Singleton @Startup` con una contraseña inicial documentada que hay que cambiar en el primer ingreso.

## Preguntas probables y respuestas cortas

**¿Por qué la cobertura es sincrónica y la autorización asincrónica, si son del mismo sistema?**
La cobertura cambia lo que el paciente paga ahora: necesitamos la respuesta (y si no llega, tenemos un
plan B). La autorización es un trámite de la auditoría médica de la obra social que puede tardar: el turno
se confirma igual y la respuesta llega por la cola sin perderse.

**¿Por qué la cobertura tiene fallback y el cobro no?** Confirmar sin conocer la cobertura es recuperable
(se resuelve después); confirmar un turno particular sin cobrarlo es regalar la consulta.

**¿Qué pasa si la falla ocurre después de cobrar?** La transacción hace rollback: base y mensajes se
revierten solos; el cobro se devuelve con la compensación. Si la pasarela también estuviera caída, el caso
queda registrado para conciliación manual (limitación documentada).

**¿Por qué no un commit en dos fases con la pasarela?** Porque un servicio REST no es un recurso XA: no
puede votar en el 2PC. Para eso están las sagas / transacciones compensatorias.

**¿Qué atributos de transacción usan y por qué?** REQUIRED en el flujo crítico; MANDATORY en `cobrar`
(sin transacción no hay compensación posible); SUPPORTS en la consulta de cobertura; NOT_SUPPORTED en la
cotización y en la conversación @Stateful (no retener conexiones mientras se espera al legado);
REQUIRES_NEW en la bitácora de integraciones y en el registro de accesos (tienen que sobrevivir al rollback).
El inicializador del administrador usa transacción manejada por el bean (BMT) para poder revertir y seguir
si la base no responde al arrancar.

**¿Por qué el reintegro por cancelación lo hace Facturación y no la cancelación?** Si se devolviera dentro
de la transacción y la cancelación fallara después, el paciente tendría el turno y el dinero. Consumiendo el
evento, sólo se reintegra una cancelación que se concretó; y el reembolso es idempotente.

**¿Cómo evitan procesar dos veces un mensaje?** Idempotent Receiver: tabla `evento_procesado` con clave
(suscriptor, id); la pasarela además deduplica por `Idempotency-Key` y el legado por `idSolicitud`.

**¿Qué es Competing Consumers acá?** `AutorizacionConsumer` con `maxSession=2`: dos sesiones sobre la misma
cola; el broker le da cada mensaje a una sola. El log muestra qué consumidor (hilo) procesó cada solicitud.

**¿Qué es Selective Consumer acá?** La suscripción de Facturación tiene el selector
`tipoEvento IN ('TURNO_AUTORIZADO','TURNO_CANCELADO')`: el broker no le entrega el resto.

**¿Por qué un Circuit Breaker propio y no MicroProfile?** MicroProfile Fault Tolerance no viene en el
perfil `standalone-full`, que necesitamos por Artemis. El documento de diseño ya preveía esta alternativa.
Tiene pruebas unitarias con un reloj controlado.

**¿Dónde está el WSDL y cómo se generó el cliente?** El legado lo publica en `?wsdl`; guardamos una copia en
el repo y wsimport genera el cliente en cada build. Las clases generadas sólo las ve el adaptador.

**¿Cómo se autentica MediConecta ante el legado?** WS-Security UsernameToken con handlers JAX-WS en los dos
extremos. En producción iría con TLS y el perfil de contraseña con resumen (limitación documentada).

**¿Por qué los MDB tienen `@RunAs("SISTEMA")` y `@PermitAll`?** Un MDB no tiene usuario: `@RunAs` le da la
identidad técnica para llamar a servicios protegidos (`autorizar`, `procesarEvento`, `reembolsar`). El
`@PermitAll` es porque WildFly deniega por defecto los métodos sin permiso en un bean que declara roles: el
contenedor invoca `onMessage` sin usuario y era rechazado (lo encontramos en las pruebas).

**¿Cómo se controla quién ve qué pantalla?** Dos capas: `@RolesAllowed` en los EJB (la barrera real, también
para la API) y, en las vistas, `LoginBean.exigirRol` en el `f:viewAction`, que devuelve a cada uno a su
página de inicio (o a Mi perfil si tiene contraseña temporal).

**¿Por qué no se puede registrar un profesional desde la web?** Porque el registro público sólo crea
pacientes, y lo decide el servicio, no la pantalla: ocultar una opción no alcanza, el pedido HTTP se puede
armar a mano. Los profesionales y administrativos los da de alta un administrativo.

**¿Cómo se vence la disponibilidad?** Un `@Singleton` con `@Schedule` (cada minuto, no persistente) borra
los horarios DISPONIBLE cuya fecha pasó y que no tienen nada asociado; mientras tanto, la búsqueda ya no
ofrece horarios pasados. Es el mismo mecanismo que libera los holds vencidos.

**¿Qué patrones nuevos hay y dónde?** Adapter/Gateway (adaptadores SOAP/REST), Circuit Breaker + Retry +
Fallback, Strategy (copago), Chain of Responsibility (validación de reserva), State (turno), Intercepting
Filter (`@Auditado`), Singleton (`CatalogoPrestaciones`), Point-to-Point, Competing Consumers, Selective
Consumer, Message Expiration y la transacción compensatoria. El Factory de usuarios ahora crea tres roles.
Ver Tabla 9, Figura A3 y el diagrama 18.

**¿Los sistemas externos son reales?** Son simuladores en un WAR aparte, pero la integración es real: HTTP,
WSDL, autenticación y fallas reales. Por eso se pueden tirar abajo en vivo.

**¿Cómo funcionan los temas sin recargar?** Todos los colores son variables CSS; el selector cambia el
atributo `data-tema` del `<html>` y el navegador recalcula al instante. Una cookie hace que el servidor
dibuje las páginas siguientes ya con ese tema; el valor se valida contra una lista cerrada.
