package ar.edu.mediconecta.negocio;

import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * D-06: cada minuto (timer del contenedor, no persistente) reintenta los reintegros pendientes a los que ya les toca,
 * con la espera creciente de {@link ar.edu.mediconecta.modelo.ReintegroPendiente}. Si la pasarela vuelve, el dinero
 * vuelve al paciente sin que nadie tenga que hacer nada.
 */
@Singleton
public class ReintentadorDeReintegros {

    private static final Logger LOG = Logger.getLogger(ReintentadorDeReintegros.class.getName());

    @Inject
    private ServicioDeReintegros reintegros;

    @Schedule(hour = "*", minute = "*", persistent = false)
    void reintentar() {
        try {
            int intentados = reintegros.reintentarVencidos();
            if (intentados > 0) {
                LOG.info("Reintegros pendientes: se reintentaron " + intentados + ".");
            }
        } catch (RuntimeException falla) {
            // Sin base al arrancar o una falla puntual: el próximo minuto se vuelve a intentar.
            LOG.log(Level.WARNING, "No se pudieron reintentar los reintegros pendientes; se reintenta en un minuto.", falla);
        }
    }
}
