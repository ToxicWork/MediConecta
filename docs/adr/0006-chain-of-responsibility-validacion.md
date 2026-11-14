# ADR-0006: Chain of Responsibility para validar una reserva

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Chain of Responsibility

## Contexto

Antes de retener o confirmar un turno hay que verificar varias reglas independientes, en un orden que importa (primero que exista, después que esté libre, etc.), y la reserva y la confirmación comparten sólo algunas.

## Decisión

Cada regla es un eslabón (`TurnoExistente`, `TurnoLibre`, `HorarioFuturo`, `SinSuperposicion`, `HoldDelPaciente`, `HoldVigente`) que valida y pasa al siguiente. `CadenaDeValidacion` arma dos cadenas: una para reservar y otra para confirmar.

## Alternativas descartadas

Un método con todas las verificaciones seguidas: no se pueden reordenar ni reutilizar entre reserva y confirmación. Bean Validation: sirve para datos de un objeto, no para reglas que consultan la base.

## Consecuencias

A favor: cada regla tiene su mensaje y su test; armar otra cadena es elegir eslabones. En contra: el flujo completo no se lee en un solo lugar.

## Dónde está en el código

Paquete `negocio/validacion`.

## Cómo se verifica

`ValidadorReservaTest` (3).
