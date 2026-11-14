# ADR-0005: Strategy para calcular el copago

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Strategy

## Contexto

Lo que paga el paciente depende de lo que responde la obra social: un porcentaje, un monto fijo, exento, sin cobertura o cobertura todavía sin confirmar. Son cinco algoritmos distintos que se eligen en tiempo de ejecución.

## Decisión

La interfaz `CalculoCopago` tiene cinco implementaciones (`CopagoPorcentual`, `CopagoMontoFijo`, `CopagoExento`, `TarifaParticular`, `CoberturaPendiente`). `SelectorCalculoCopago` elige la que corresponde al resultado de la cobertura.

## Alternativas descartadas

Un método con condicionales: cada regla nueva de una obra social obliga a modificarlo y a volver a probar todas las demás.

## Consecuencias

A favor: cada regla se prueba sola y agregar una es una clase nueva. En contra: el selector tiene que conocer el criterio de elección.

## Dónde está en el código

Paquete `negocio/copago`.

## Cómo se verifica

`CalculoCopagoTest` (5); `prueba_entrega4.py` escenario C (liquidación de $32.000 con 30 % de copago).
