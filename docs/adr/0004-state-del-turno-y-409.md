# ADR-0004: State para el ciclo de vida del turno, con 409 ante una transición inválida

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** State

## Contexto

Un turno pasa por DISPONIBLE, HOLD, CONFIRMADO y CANCELADO, y no toda operación vale en todo estado. Con condicionales en cada servicio, una regla olvidada dejaba cancelar un turno disponible o confirmar uno cancelado. Además, la API REST necesita decirle al cliente que su pedido es correcto pero choca con el estado del turno.

## Decisión

La entidad `Turno` guarda un enum (`EstadoTurno`) y delega cada operación en el objeto de estado asociado (`TurnoDisponible`, `TurnoEnHold`, `TurnoConfirmado`, `TurnoCancelado`). La interfaz `EstadoDeTurno` rechaza toda transición por defecto con `TransicionInvalidaException`; cada estado redefine sólo las que admite. La excepción revierte la transacción y `MapeadorDeExcepciones` la traduce a **409 Conflict** con el código `transicion_invalida`. Los servicios no preguntan por el estado: `ProcesoCancelacionTurno` verifica la titularidad y llama a `turno.cancelar()`.

## Alternativas descartadas

`switch` sobre el estado en cada servicio: la regla queda repartida y es fácil olvidarla. Responder 400: el pedido no está mal formado. Responder 422: se usa para datos inválidos, no para un conflicto con el estado actual del recurso, que es exactamente lo que define 409 (RFC 9110, 15.5.10).

## Consecuencias

A favor: una transición inválida es imposible aunque un servicio nuevo se olvide de validar; el cliente distingue por código (`transicion_invalida` frente a `regla_de_negocio`). En contra: cuatro clases chicas más; el estado se persiste como enum y el comportamiento se resuelve en memoria.

## Dónde está en el código

`modelo/Turno`, `modelo/EstadoTurno`, `modelo/EstadoDeTurno` y los cuatro estados; `negocio/ProcesoCancelacionTurno`; `api/TurnosResource` (`POST /turnos/{id}/cancelacion`); `api/MapeadorDeExcepciones`.

## Cómo se verifica

`TurnoEstadoTest` (6, incluida la matriz completa de transiciones inválidas), `MapeadorDeExcepcionesTest` (4), `prueba_entrega4.py` escenario N (por HTTP) y `demo_api_jwt_409.py`.
