# ADR-0024: Reintegros pendientes, y la falla de demostración fuera de la aplicación

- **Estado:** aceptado (19/11/2026)
- **Fecha:** 19/11/2026
- **Patrón:** Transacción compensatoria con reintentos (outbox de reintegros); Retry con espera exponencial
- **Resuelve:** D-06 y D-41 (el reintegro se perdía si la pasarela estaba caída o no conocía el pago) y D-42 (la falla
  de demostración vivía en la aplicación). Completa la compensación del ADR-0013.

## Contexto

La pasarela es REST y no participa de la transacción XA. Si la confirmación se revierte después de cobrar,
`CompensacionDePago` pide el reembolso cuando la transacción termina sin commit (ADR-0013). Había dos huecos:

- **D-06:** si en ese momento la pasarela no respondía, el error quedaba sólo en el log. El dinero quedaba cobrado y
  nadie se enteraba.
- **D-41:** al cancelar, si la pasarela respondía que no conoce el pago (404), pasaba lo mismo.

La cancelación con la pasarela caída ya estaba resuelta: el mensaje vuelve al broker, termina en la DLQ y se reprocesa
(SEC-I02). Ese camino no cambia.

Además, para mostrar el rollback y la compensación, la aplicación tenía una «falla de demostración» (`InyectorDeFallas`
y un botón en Pagos), apagada salvo con una propiedad del servidor. El informe del núcleo prometió retirarla o pasarla
a una prueba de integración (D-42).

## Decisión

- **Reintegros pendientes** (`modelo/ReintegroPendiente`, tabla `reintegro_pendiente`):
  - si la pasarela no responde, el reintegro se guarda **pendiente**, en una transacción nueva (la de la confirmación
    ya terminó);
  - `ReintentadorDeReintegros` (timer del contenedor, cada minuto) lo reintenta con espera creciente: 1, 2, 4, 8...
    minutos, hasta una hora. Después de 10 intentos pasa a conciliación manual;
  - si la pasarela responde que no conoce el pago o lo rechaza, va directo a **conciliación manual**: reintentar no lo
    arregla;
  - la pasarela es idempotente, así que reintentar un reintegro ya hecho no devuelve dos veces.
- **Pagos (administrativo)**: una tarjeta con los reintegros abiertos, con «Reintentar ahora» y «Ya lo concilié» (queda
  quién lo resolvió).
- **D-42**: se borraron `InyectorDeFallas`, `ControlDeDemostracion`, `FallaSimuladaException` y el botón de Pagos. La
  misma situación la provoca `CompensacionIT` (Arquillian). Un EJB de prueba (`ConfirmacionQueFalla`) corre la
  confirmación real dentro de su transacción y lanza una excepción antes del commit. La prueba verifica:
  - que el turno sigue retenido, el pago no quedó y la pasarela figura con el cobro reembolsado;
  - con el simulador en modo «sin reembolsos», que el reintegro queda pendiente y se hace cuando la pasarela vuelve;
  - que un pago que la pasarela no conoce queda para conciliación manual.

## Consecuencias

- **A favor:**
  - Ningún reintegro se pierde en silencio, y los que necesitan a una persona están a la vista.
  - El código de producción ya no tiene un camino para hacer fallar transacciones a propósito.
- **En contra:**
  - Si fallan a la vez la pasarela y la base, el reintegro sigue quedando sólo en el log (no hay dónde guardarlo).
  - La demostración en vivo del rollback ya no se hace desde la pantalla, sino corriendo `CompensacionIT`.
- **Pruebas:**
  - `ReintegroPendienteTest`, `ServicioDeReintegrosBeanTest` y las de compensación en `ServicioDePagosBeanTest`.
  - `CompensacionIT`: 3 pruebas contra la base, el broker y la pasarela reales.
  - El escenario F de `prueba_entrega4.py` verifica que Pagos ya no tiene la herramienta y sí los reintegros.
