# ADR-0008: Circuit Breaker, Retry y Fallback propios frente al legado

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Circuit Breaker + Retry + Fallback

## Contexto

El sistema de la obra social es lento y se cae. Sin protección, cada consulta de cobertura dejaría un hilo esperando el timeout y la reserva de turnos quedaría inutilizable por una falla ajena.

## Decisión

El adaptador SOAP aplica timeout (2 s de conexión y 3 s de respuesta), un reintento a los 500 ms sólo en la consulta de cobertura (no tiene efectos), un Circuit Breaker que se abre tras 3 fallas seguidas durante 30 s y un fallback: la última validación exitosa (hasta 24 h) o cobertura PENDIENTE. Las clases `CircuitBreaker` y `Reintento` son propias. **Reemplaza lo previsto en el documento de diseño**, que proponía MicroProfile Fault Tolerance.

## Alternativas descartadas

MicroProfile Fault Tolerance: no viene en el perfil `standalone-full` de WildFly, que es el que trae el broker de mensajería. Agregar una biblioteca externa (Resilience4j): una dependencia más para tres clases chicas. No reintentar el cobro ni darle fallback: decisión deliberada, porque confirmar sin cobrar es regalar la consulta.

## Consecuencias

A favor: si el legado cae, el paciente igual reserva y confirma; el circuito se prueba con un reloj controlado. En contra: mantenemos código de resiliencia propio y el estado del circuito vive en memoria (se pierde al reiniciar).

## Dónde está en el código

`integracion/resiliencia/CircuitBreaker`, `Reintento`; `integracion/obrasocial/CoberturaSoapAdapter`.

## Cómo se verifica

`CircuitBreakerTest` (5), `ReintentoTest` (3); `prueba_entrega4.py` escenario G (legado caído).
