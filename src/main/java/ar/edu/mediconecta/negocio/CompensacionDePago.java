package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.integracion.pagos.PagosGateway;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transacción compensatoria del cobro. La pasarela es REST y no participa de la transacción XA: si la
 * confirmación hace rollback después de cobrar, la base y los mensajes se revierten solos pero el dinero ya
 * salió. Esta {@link Synchronization} se registra en la transacción al cobrar y, cuando la transacción
 * termina sin commit, pide el reembolso (la pasarela lo hace idempotente).
 */
class CompensacionDePago implements Synchronization {

    private static final Logger LOG = Logger.getLogger(CompensacionDePago.class.getName());

    private final PagosGateway pasarela;
    private final String idPago;
    private final String referencia;

    CompensacionDePago(PagosGateway pasarela, String idPago, String referencia) {
        this.pasarela = pasarela;
        this.idPago = idPago;
        this.referencia = referencia;
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
            pasarela.reembolsarCompensacion(idPago, "Reversión automática: la confirmación del turno no se completó",
                    referencia);
            LOG.warning("Compensación: la transacción terminó sin commit (estado " + estado + "), se reembolsó el pago "
                    + idPago + ".");
        } catch (RuntimeException falla) {
            // No se puede propagar desde afterCompletion: queda registrado para conciliación manual.
            LOG.log(Level.SEVERE, "No se pudo reembolsar el pago " + idPago + " tras el rollback: requiere "
                    + "conciliación manual con la pasarela.", falla);
        }
    }
}
