package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.negocio.excepciones.FallaSimuladaException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Herramienta de demostración (no es parte del negocio): se arma desde el panel de integraciones y hace
 * fallar la próxima confirmación al final del flujo, antes del commit, para mostrar en vivo el rollback y
 * la compensación del cobro. Se desarma sola después de disparar.
 */
@ApplicationScoped
public class InyectorDeFallas {

    private static final Logger LOG = Logger.getLogger(InyectorDeFallas.class.getName());

    private final AtomicBoolean armada = new AtomicBoolean(false);

    public void armar() {
        armada.set(true);
        LOG.warning("Falla de demostración ARMADA: la próxima confirmación de turno va a fallar antes del commit.");
    }

    public void desarmar() {
        armada.set(false);
    }

    public boolean isArmada() {
        return armada.get();
    }

    void fallarSiEstaArmada() {
        if (armada.compareAndSet(true, false)) {
            LOG.warning("Disparando la falla de demostración: la transacción de la confirmación va a hacer rollback.");
            throw new FallaSimuladaException();
        }
    }
}
