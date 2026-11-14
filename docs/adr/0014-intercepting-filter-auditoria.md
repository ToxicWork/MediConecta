# ADR-0014: Intercepting Filter para auditar el acceso a la historia clínica

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Intercepting Filter

## Contexto

La historia clínica es el dato más sensible: hay que registrar quién la consultó y cuándo. Escribir ese registro dentro de cada método lo repite y es fácil olvidarlo en una operación nueva.

## Decisión

La anotación `@Auditado` activa un interceptor CDI (`AuditoriaInterceptor`) que envuelve la operación y registra usuario, recurso y resultado. El paciente ve ese registro en su pantalla de historia clínica.

## Alternativas descartadas

Un filtro HTTP: no cubre las llamadas que llegan por la API o por mensajería y no conoce la operación de negocio. Registrar a mano en cada método: se olvida.

## Consecuencias

A favor: auditar una operación es poner una anotación. En contra: el interceptor sólo actúa en llamadas que pasan por el contenedor.

## Dónde está en el código

Paquete `auditoria`; `negocio/ServicioDeHistoriaClinicaBean`.

## Cómo se verifica

`prueba_entrega4.py` escenario J: el paciente ve qué profesional consultó su historia.
