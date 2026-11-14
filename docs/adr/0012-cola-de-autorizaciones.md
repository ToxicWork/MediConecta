# ADR-0012: Cola punto a punto con consumidores en competencia para las autorizaciones

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Point-to-Point + Competing Consumers + Dead Letter Channel + Message Expiration

## Contexto

La obra social tiene que autorizar la prestación, pero su auditoría puede tardar o estar caída. El paciente no tiene por qué esperar ese trámite para tener su turno, y el pedido no se puede perder ni procesar dos veces.

## Decisión

La confirmación encola un mensaje en `cola.autorizaciones`. `AutorizacionConsumer` (MDB con `maxSession=2`: dos consumidores compiten y el broker le da cada mensaje a uno solo) llama al legado por SOAP. Si falla, hace `setRollbackOnly` y el broker reintenta con espera creciente (10, 20, 40 y 60 s, hasta 8 intentos); después el mensaje va a la cola de mensajes muertos. Los mensajes vencen a las 24 h.

## Alternativas descartadas

Autorizar en línea durante la confirmación: una caída del legado impediría confirmar turnos. Un tópico: la autorización la tiene que procesar un solo consumidor, no todos.

## Consecuencias

A favor: el turno se confirma con el legado caído y la autorización se resuelve sola cuando vuelve. En contra: el turno queda un tiempo con la autorización «en trámite» y hay que revisar la cola de mensajes muertos.

## Dónde está en el código

`mensajeria/ProductorSolicitudesAutorizacion`, `AutorizacionConsumer`; `wildfly/configurar-integracion-entrega4.cli`.

## Cómo se verifica

`prueba_entrega4.py` escenarios D (se resuelve sola) y G (legado caído).
