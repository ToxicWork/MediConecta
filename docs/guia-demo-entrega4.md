# Guía de la demo y la defensa · Entrega Obligatoria N.º 2 (09/11)

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
- [ ] Tres pestañas: paciente (`juan.perez@mediconecta.test`), profesional (`ana.lopez@mediconecta.test`),
      ambos con `demo1234`, y la consola de externos.
- [ ] La profesional publicó dos horarios futuros: uno **Presencial** y uno **Telemedicina**.
- [ ] Opcional: correr `python pruebas/prueba_entrega4.py` (36 verificaciones, ~2 min) como prueba de humo.

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
5. **Monitor de eventos**: aparece TURNO_CONFIRMADO procesado por Notificaciones y Auditoría, y
   **Facturación "no aplica"** (lo filtra el selector en el broker). En **cola.autorizaciones** la solicitud
   pasa de "En cola" a **"Autorizada AUT-…"** en ~2 s, con el consumidor que la procesó.
6. Aparece un segundo evento **TURNO_AUTORIZADO**, que esta vez sí procesa Facturación.
7. **Profesional → Pagos**: el cobro de $9.600 y el **reclamo a OSDE por $22.400** con el código de
   autorización. **Notificaciones** de los dos: confirmación y autorización.

Decir: un solo clic del paciente cruzó cuatro componentes internos, un SOAP, un REST, una cola y un tópico.

## Bloque 3 · Falla a mitad del flujo crítico (5 min) — el corazón de la defensa

1. **Integraciones → "Armar falla antes del commit"**. Explicar la lista de 6 pasos del panel.
2. Paciente: reservar otro horario y **Pagar y confirmar** → cartel: *"Falla simulada al final de la
   confirmación, antes del commit: se revirtió todo… y el cobro se reembolsó automáticamente."*
3. Mostrar:
   - "Mis turnos" no cambió y el turno **sigue en espera** (el bean @Stateful no se destruyó).
   - Monitor: **no hay** TURNO_CONFIRMADO ni solicitud de autorización para ese turno.
   - Consola de externos → Pagos: el cobro figura **REEMBOLSADO** con motivo "Reversión automática…".
   - Integraciones → bitácora: `POST /pagos` OK y `POST /pagos/{id}/reembolso (compensación)`
     **COMPENSACION**. Estas filas quedaron aunque la transacción hizo rollback porque se escriben con
     **REQUIRES_NEW**.
4. Volver a confirmar: ahora sí se confirma (la falla se desarma sola).

Decir (Tabla 8 del documento): la **base y el broker** se revierten solos porque participan de la misma
transacción JTA (el envío JMS es XA: Transactional Client); la **pasarela REST no es transaccional**, así
que el cobro se compensa con un reembolso desde una `Synchronization` registrada en la transacción
(`CompensacionDePago.afterCompletion`); la **consulta SOAP** no tiene efectos, no hay nada que compensar.

## Bloque 4 · El legado se cae (4 min) — resiliencia (desafío opcional)

1. Consola de externos: Obra social → **Caído**.
2. Paciente: reservar → la cotización dice *"El sistema de la obra social no respondió: se usó la última
   validación vigente (caché)"* (o "cobertura pendiente" si no había caché). **Integraciones**: el circuito
   de la obra social **ABIERTO**, con la cuenta regresiva.
3. Confirmar: funciona igual. Monitor: la solicitud de autorización queda **"En cola"** con intentos fallidos.
4. Consola: Obra social → **En línea**. En menos de un minuto la solicitud pasa sola a **Autorizada**.

Decir: timeout (2 s / 3 s) → un reintento (sólo en la consulta, que es idempotente) → Circuit Breaker
(3 fallas, 30 s abierto) → fallback con caché. El circuito evita colgar hilos esperando timeouts. La
autorización no se pierde: el consumidor hace `setRollbackOnly` y el broker reintenta con espera creciente
(10, 20, 40, 60 s; 8 intentos y DLQ). Con el modo **Lento** se ve el timeout: corta a los 3 s.

## Bloque 5 · Cancelación, telemedicina y API (3 min)

- Paciente cancela el turno pagado → "El reintegro… se procesa en unos segundos". En Pagos pasa a
  **Reembolsado**; el reclamo del profesional pasa a **Anulado**. Lo hizo Facturación al consumir
  TURNO_CANCELADO, **después del commit** de la cancelación.
- Teleconsulta: la profesional entra a **Telemedicina → Iniciar videoconsulta** (POST REST al proveedor);
  el paciente ve **Unirme a la consulta** sin recargar.
- API: en una terminal,
  `curl -i http://localhost:8080/mediconecta/api/v1/turnos` → 401;
  `curl -u juan.perez@mediconecta.test:demo1234 http://localhost:8080/mediconecta/api/v1/turnos` → JSON.

## Bloque 6 · Temas (30 s)

Cambiar **Estándar → Claro → Oscuro** desde la barra superior en cualquier pantalla: el cambio es
instantáneo, sin recargar, y se mantiene al navegar.

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

**¿Por qué el reintegro por cancelación lo hace Facturación y no la cancelación?** Si se devolviera dentro
de la transacción y la cancelación fallara después, el paciente tendría el turno y el dinero. Consumiendo el
evento, sólo se reintegra una cancelación que se concretó; y el reembolso es idempotente.

**¿Cómo evitan procesar dos veces un mensaje?** Idempotent Receiver: tabla `evento_procesado` con clave
(suscriptor, id); la pasarela además deduplica por `Idempotency-Key` y el legado por `idSolicitud`.

**¿Qué es Competing Consumers acá?** `AutorizacionConsumer` con `maxSession=2`: dos sesiones sobre la misma
cola; el broker le da cada mensaje a una sola. El monitor muestra qué consumidor procesó cada solicitud.

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

**¿Qué patrones nuevos hay y dónde?** Adapter/Gateway (adaptadores SOAP/REST), Circuit Breaker + Retry +
Fallback, Strategy (copago), Chain of Responsibility (validación de reserva), State (turno), Intercepting
Filter (`@Auditado`), Singleton (`CatalogoPrestaciones`), Point-to-Point, Competing Consumers, Selective
Consumer, Message Expiration y la transacción compensatoria. Ver Tabla 9 y Figura A3.

**¿Los sistemas externos son reales?** Son simuladores en un WAR aparte, pero la integración es real: HTTP,
WSDL, autenticación y fallas reales. Por eso se pueden tirar abajo en vivo.

**¿Cómo funcionan los temas sin recargar?** Todos los colores son variables CSS; el selector cambia el
atributo `data-tema` del `<html>` y el navegador recalcula al instante. Una cookie hace que el servidor
dibuje las páginas siguientes ya con ese tema; el valor se valida contra una lista cerrada.
