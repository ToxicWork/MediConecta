# ADR-0013: Transactional Client y transacción compensatoria en la confirmación

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Transactional Client + compensación

## Contexto

Confirmar un turno toca la base, dos destinos de mensajería y la pasarela de pagos. Si falla a mitad de camino no puede quedar un turno confirmado sin cobro, ni un cobro sin turno, ni un aviso de algo que no ocurrió.

## Decisión

`ProcesoConfirmacionTurno.confirmar` es una transacción JTA declarativa (`REQUIRED`). La base y el broker participan de esa transacción (el broker como recurso XA; la base, con un datasource `jta=true`, como último recurso): si hay rollback, los datos se revierten y los mensajes no salen. La pasarela es REST y no puede participar: al cobrar se registra una `Synchronization` (`CompensacionDePago`) que, si la transacción no confirma, pide el reembolso. La bitácora de integraciones usa `REQUIRES_NEW` para sobrevivir al rollback.

## Alternativas descartadas

Commit en dos fases con la pasarela: un servicio REST no es un recurso XA. Cobrar después de confirmar: dejaría turnos confirmados sin pagar si el cobro falla.

## Consecuencias

A favor: ante una falla el sistema vuelve a un estado coherente sin intervención. En contra: si la pasarela también está caída al compensar, el caso queda para conciliación manual (ver defectos conocidos).

## Dónde está en el código

`negocio/ProcesoConfirmacionTurno`, `negocio/CompensacionDePago`, `mensajeria/PublicadorEventosTurno`.

## Cómo se verifica

`prueba_entrega4.py` escenario C (pago rechazado: el turno sigue en espera) y escenario F (falla armada después de cobrar: rollback de la base y de los mensajes, y reembolso compensatorio visible en la pasarela).
