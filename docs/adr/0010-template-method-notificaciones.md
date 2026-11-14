# ADR-0010: Template Method y Factory Method para las notificaciones

- **Estado:** aceptado
- **Fecha:** 19/10/2026
- **Patrón:** Template Method + Factory Method

## Contexto

Toda notificación sigue los mismos pasos (armar, enviar, registrar) y sólo cambia el contenido según el canal y el destinatario.

## Decisión

`ProcesadorNotificacion.procesar` fija el orden de los pasos y es `final`. El paso de creación (`crearNotificacion`) lo redefine cada subclase: `ProcesadorEmail` para el paciente y `ProcesadorAvisoProfesional` para el profesional.

## Alternativas descartadas

Una clase por canal sin base común: repite el registro y el manejo de errores. Strategy: sirve para cambiar un algoritmo entero; acá lo fijo es el esqueleto y lo variable un paso.

## Consecuencias

A favor: un canal nuevo (SMS, push) es una subclase. En contra: herencia; las subclases dependen del orden que fija la base.

## Dónde está en el código

Paquete `notificaciones`.

## Cómo se verifica

`prueba_e2e.py` y `prueba_entrega4.py` escenario D: el paciente y el profesional reciben sus avisos.
