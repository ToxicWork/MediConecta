# ADR-0009: Builder para los eventos del turno

- **Estado:** aceptado
- **Fecha:** 19/10/2026
- **Patrón:** Builder

## Contexto

Un evento de turno tiene más de diez campos, varios opcionales según el tipo (confirmado, cancelado, autorizado). Con un constructor largo era fácil cruzar argumentos del mismo tipo.

## Decisión

`EventoTurno.builder()` arma el evento paso a paso, toma los datos del `Turno` y valida en `build()` que estén el tipo, el turno y su paciente.

## Alternativas descartadas

Constructor con todos los parámetros: ilegible y propenso a errores. Setters: permiten publicar un evento a medio armar.

## Consecuencias

A favor: no se puede publicar un evento sin tipo o sin turno. En contra: más código que un constructor.

## Dónde está en el código

`mensajeria/EventoTurno`.

## Cómo se verifica

`EventoTurnoTest` (2).
