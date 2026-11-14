# ADR-0016: Transfer Object para la API REST

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Transfer Object (DTO)

## Contexto

Si la API devolviera las entidades JPA, expondría campos internos (hash de la contraseña, datos clínicos) y ataría el contrato público al modelo de datos.

## Decisión

La API devuelve registros propios (`TurnoApi`, `ProfesionalApi`, `TurnoDisponibleApi`, `TokenApi`, `ErrorApi`) que arma `EnsambladorApi` con sólo los campos que el consumidor necesita. Todos los errores usan el mismo formato y nunca incluyen trazas.

## Alternativas descartadas

Anotar las entidades para ocultar campos: un campo nuevo queda expuesto por defecto.

## Consecuencias

A favor: cambiar la base no rompe la API; lo sensible no sale porque nunca se copia. En contra: estructuras duplicadas y un ensamblador que mantener.

## Dónde está en el código

`api/dto`, `api/EnsambladorApi`.

## Cómo se verifica

`prueba_entrega4.py` escenario B; `MapeadorDeExcepcionesTest` (un error 500 no filtra nombres internos).
