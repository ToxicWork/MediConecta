# ADR-0002: Session Facade con estado para la reserva de turnos

- **Estado:** aceptado
- **Fecha:** 14/09/2026
- **Patrón:** Facade (Session Facade)

## Contexto

Reservar un turno son varios pasos (buscar, retener cinco minutos, cotizar, pagar, confirmar o liberar). Si la pantalla coordinara esos pasos, cada cliente repetiría la secuencia y podría saltearse uno.

## Decisión

`ServicioDeTurnosBean` expone la conversación completa detrás de una interfaz (`ServicioDeTurnos`). Es `@Stateful` con `@StatefulTimeout` de cinco minutos: recuerda qué turno retuvo el paciente y, si no confirma, el contenedor llama a `@PreDestroy` y libera el turno.

## Alternativas descartadas

Un servicio sin estado con el hold guardado sólo en la base: obliga a pasar el id en cada llamada y a confiar en que el cliente no lo cambie. Guardar el hold en la sesión HTTP: ata la regla a la web y la API no podría usarla.

## Consecuencias

A favor: un punto de entrada, con la seguridad declarativa (`@RolesAllowed`) en cada operación. En contra: un bean con estado no sirve a la API REST, que es sin estado; por eso las consultas están en `ServicioDeAgenda` y la cancelación en `ProcesoCancelacionTurno`, ambos `@Stateless`.

## Dónde está en el código

`negocio/ServicioDeTurnos`, `negocio/ServicioDeTurnosBean`.

## Cómo se verifica

`prueba_e2e.py`: reserva, vencimiento del hold y confirmación.
