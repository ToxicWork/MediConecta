package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.negocio.excepciones.FallaSimuladaException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Herramienta de demostración (no es parte del negocio): hace fallar la próxima confirmación al final del flujo,
 * antes del commit, para mostrar en vivo el rollback y la compensación del cobro. Se desarma sola después de disparar.
 *
 * <p>Está apagada salvo que el servidor tenga la propiedad de sistema {@value #PROPIEDAD} en {@code true} (para la
 * defensa: {@code standalone.bat -Dmediconecta.demo.fallas=true}, o en caliente
 * {@code /system-property=mediconecta.demo.fallas:add(value=true)} desde jboss-cli). Apagada, no se puede armar, la
 * pantalla de Pagos no la muestra y la confirmación nunca falla por ella: el informe del núcleo prometió retirarla de
 * la entrega final.
 */
@ApplicationScoped
public class InyectorDeFallas {

    public static final String PROPIEDAD = "mediconecta.demo.fallas";

    private static final Logger LOG = Logger.getLogger(InyectorDeFallas.class.getName());

    private final AtomicBoolean armada = new AtomicBoolean(false);

    /** Si el servidor la habilitó para una demostración. */
    public boolean isHabilitada() {
        return Boolean.getBoolean(PROPIEDAD);
    }

    public void armar() {
        if (!isHabilitada()) {
            throw new ErrorDeNegocioException("La falla de demostración está apagada: se habilita levantando el servidor "
                    + "con -D" + PROPIEDAD + "=true.");
        }
        armada.set(true);
        LOG.warning("Falla de demostración ARMADA: la próxima confirmación de turno va a fallar antes del commit.");
    }

    public void desarmar() {
        armada.set(false);
    }

    public boolean isArmada() {
        return isHabilitada() && armada.get();
    }

    void fallarSiEstaArmada() {
        if (isHabilitada() && armada.compareAndSet(true, false)) {
            LOG.warning("Disparando la falla de demostración: la transacción de la confirmación va a hacer rollback.");
            throw new FallaSimuladaException();
        }
    }
}
