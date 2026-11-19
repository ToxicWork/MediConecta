package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.integracion.pagos.PagosGateway;
import ar.edu.mediconecta.integracion.pagos.ResultadoCobro;
import ar.edu.mediconecta.modelo.OrigenReintegro;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transacción compensatoria del cobro. La pasarela es REST y no participa de la transacción XA: si la
 * confirmación hace rollback después de cobrar, la base y los mensajes se revierten solos pero el dinero ya
 * salió. Esta {@link Synchronization} se registra en la transacción al cobrar y, cuando la transacción
 * termina sin commit, pide el reembolso (la pasarela lo hace idempotente).
 *
 * <p>D-06: si la pasarela no responde en ese momento, el reintegro queda pendiente en {@link ServicioDeReintegros}
 * (en una transacción nueva, porque la de la confirmación ya terminó) y se reintenta solo. Si responde que no conoce
 * el pago, queda para conciliación manual. Antes, en los dos casos sólo quedaba en el log.
 */
class CompensacionDePago implements Synchronization {

    private static final Logger LOG = Logger.getLogger(CompensacionDePago.class.getName());
    static final String MOTIVO = "Reversión automática: la confirmación del turno no se completó";

    private final PagosGateway pasarela;
    private final ServicioDeReintegros reintegros;
    private final String idPago;
    private final String referencia;
    private final Long turnoId;
    private final BigDecimal importe;

    CompensacionDePago(PagosGateway pasarela, ServicioDeReintegros reintegros, String idPago, String referencia,
                       Long turnoId, BigDecimal importe) {
        this.pasarela = pasarela;
        this.reintegros = reintegros;
        this.idPago = idPago;
        this.referencia = referencia;
        this.turnoId = turnoId;
        this.importe = importe;
    }

    @Override
    public void beforeCompletion() {
        // Nada que hacer antes del commit.
    }

    @Override
    public void afterCompletion(int estado) {
        if (estado == Status.STATUS_COMMITTED) {
            return;
        }
        try {
            ResultadoCobro resultado = pasarela.reembolsarCompensacion(idPago, MOTIVO, referencia);
            if ("REEMBOLSADO".equals(resultado.estado())) {
                LOG.warning("Compensación: la transacción terminó sin commit (estado " + estado + "), se reembolsó el pago "
                        + idPago + ".");
            } else {
                reintegros.registrarParaConciliar(idPago, referencia, turnoId, importe, MOTIVO, OrigenReintegro.COMPENSACION,
                        resultado.inexistente() ? "La pasarela no conoce el pago" : "La pasarela lo rechazó: " + resultado.resumen());
            }
        } catch (RuntimeException sinRespuesta) { // IntegracionNoDisponibleException: caída o circuito abierto
            registrarPendiente(sinRespuesta);
        }
    }

    private void registrarPendiente(RuntimeException falla) {
        try {
            reintegros.registrarParaReintentar(idPago, referencia, turnoId, importe, MOTIVO, OrigenReintegro.COMPENSACION,
                    falla.getMessage());
        } catch (RuntimeException tampoco) {
            // Ni la pasarela ni la base: no se puede propagar desde afterCompletion, queda en el log para conciliar.
            LOG.log(Level.SEVERE, "No se pudo reembolsar el pago " + idPago + " tras el rollback ni registrarlo como "
                    + "pendiente: requiere conciliación manual con la pasarela.", tampoco);
        }
    }
}
