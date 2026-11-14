# ADR-0011: Tópico con suscriptores durables e idempotentes para los eventos del turno

- **Estado:** aceptado
- **Fecha:** 19/10/2026
- **Patrón:** Publish-Subscribe + Durable Subscriber + Idempotent Receiver + Selective Consumer

## Contexto

Cuando un turno se confirma o se cancela se enteran Notificaciones, Auditoría y Facturación. Si el servicio de turnos los llamara uno por uno, quedaría acoplado a los tres y una falla de cualquiera haría fallar la confirmación.

## Decisión

`ServicioDeTurnos` publica un `EventoTurno` en `topico.turnos` (ActiveMQ Artemis embebido en WildFly). Cada interesado es un MDB con suscripción durable: no pierde eventos si estaba caído. Como el broker puede entregar dos veces, cada suscriptor registra el evento en `evento_procesado` (clave: suscriptor e id) y descarta los repetidos. Facturación usa un selector para recibir sólo `TURNO_AUTORIZADO` y `TURNO_CANCELADO`. Un mensaje que falla tres veces va a la cola de mensajes muertos.

## Alternativas descartadas

Llamadas directas entre servicios: acoplamiento y falla en cascada. Una cola por interesado: el publicador tendría que conocerlos a todos.

## Consecuencias

A favor: agregar un interesado no toca al servicio de turnos (Facturación se sumó en la entrega siguiente sin cambiarlo). En contra: consistencia eventual: la notificación llega segundos después; hace falta la tabla de idempotencia.

## Dónde está en el código

`mensajeria/PublicadorEventosTurno`, `NotificacionesSubscriber`, `AuditoriaSubscriber`, `FacturacionSubscriber`; `wildfly/configurar-mensajeria.cli`.

## Cómo se verifica

`prueba_e2e.py`; `prueba_entrega4.py` escenarios D y H (reclamo y reintegro generados por Facturación).
