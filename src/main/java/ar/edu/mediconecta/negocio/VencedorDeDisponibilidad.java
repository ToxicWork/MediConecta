package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Vencimiento automático de la disponibilidad publicada: un horario que ningún paciente reservó deja de tener
 * sentido cuando su fecha pasa, así que se elimina de la agenda. Mismo mecanismo que {@link LiberadorDeHolds}:
 * un timer del contenedor EJB (cada minuto, no persistente) en un {@code @Singleton}, que por defecto serializa
 * las ejecuciones y corre cada una en su propia transacción (REQUIRED).
 *
 * <p>Si el servidor estuvo apagado, la primera ejecución al levantar limpia todo lo vencido mientras tanto; y
 * entre ejecuciones la búsqueda de turnos ({@code TurnoDAO.buscarDisponibles}) ya no ofrece horarios pasados.
 */
@Singleton
public class VencedorDeDisponibilidad {

    private static final Logger LOG = Logger.getLogger(VencedorDeDisponibilidad.class.getName());

    @Inject
    private TurnoDAO turnoDAO;

    @Schedule(hour = "*", minute = "*", persistent = false)
    void eliminarVencidos() {
        int eliminados = turnoDAO.eliminarDisponiblesVencidos(LocalDateTime.now());
        if (eliminados > 0) {
            LOG.info("Disponibilidad vencida: se eliminaron " + eliminados + " horario(s) que nadie reservó.");
        }
    }
}
