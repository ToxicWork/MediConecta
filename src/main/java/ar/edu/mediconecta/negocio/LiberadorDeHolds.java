package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Red de seguridad del hold: el {@code @StatefulTimeout} libera el turno cuando expira la conversación, pero
 * si el servidor se reinicia o la instancia se descarta, el turno quedaría retenido para siempre. Este timer
 * del contenedor (cada minuto, no persistente) libera los holds vencidos.
 */
@Singleton
public class LiberadorDeHolds {

    private static final Logger LOG = Logger.getLogger(LiberadorDeHolds.class.getName());

    @Inject
    private TurnoDAO turnoDAO;

    @Schedule(hour = "*", minute = "*", persistent = false)
    void liberarVencidos() {
        List<Turno> vencidos = turnoDAO.buscarHoldsVencidos(LocalDateTime.now());
        for (Turno turno : vencidos) {
            turno.liberar();
            LOG.info("Hold vencido del turno " + turno.getId() + " liberado por el temporizador.");
        }
    }
}
