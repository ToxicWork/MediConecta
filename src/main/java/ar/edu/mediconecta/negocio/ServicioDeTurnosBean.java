package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.excepciones.TurnoNoDisponibleException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.PostActivate;
import jakarta.ejb.PrePassivate;
import jakarta.ejb.Stateful;
import jakarta.ejb.StatefulTimeout;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Fachada (patrón Facade) del subproceso de reserva de turnos: agrupa búsqueda de
 * disponibilidad, hold temporal y confirmación detrás de una interfaz simple.
 *
 * Es {@code @Stateful} porque el hold de un turno (~5 min) es conversacional: vive
 * atado a la sesión del paciente mientras decide confirmar. El {@code @StatefulTimeout}
 * hace que el propio contenedor EJB invoque {@code @PreDestroy} si el paciente no
 * confirma a tiempo, liberando el turno automáticamente — evidencia concreta de que
 * el contenedor gestiona el ciclo de vida de este componente.
 */
@Stateful
@StatefulTimeout(value = 5, unit = TimeUnit.MINUTES)
public class ServicioDeTurnosBean implements ServicioDeTurnos {

    private static final Logger LOG = Logger.getLogger(ServicioDeTurnosBean.class.getName());

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private UsuarioDAO usuarioDAO;

    private Long turnoEnHoldId;

    @PostConstruct
    void iniciarConversacion() {
        LOG.info("Conversación de reserva de turno iniciada por el contenedor.");
    }

    @Override
    public Turno publicarDisponibilidad(Long profesionalId, LocalDateTime fechaHora) {
        Turno turno = new Turno(usuarioDAO.obtenerReferenciaProfesional(profesionalId), fechaHora);
        turnoDAO.guardar(turno);
        return turno;
    }

    @Override
    public List<Turno> buscarDisponibilidad(Long profesionalId) {
        return turnoDAO.buscarDisponibles(profesionalId);
    }

    @Override
    public Turno reservarHold(Long turnoId, Long pacienteId) {
        if (turnoEnHoldId != null) {
            throw new TurnoNoDisponibleException("Ya existe un turno en hold en esta conversación.");
        }
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.DISPONIBLE) {
            throw new TurnoNoDisponibleException("El turno ya no está disponible.");
        }
        turno.marcarEnHold(usuarioDAO.obtenerReferenciaPaciente(pacienteId));
        turnoDAO.actualizar(turno);
        turnoEnHoldId = turno.getId();
        return turno;
    }

    @Override
    public Turno confirmar() {
        if (turnoEnHoldId == null) {
            throw new TurnoNoDisponibleException("No hay ningún turno en hold para confirmar.");
        }
        Turno turno = turnoDAO.buscarPorId(turnoEnHoldId);
        turno.confirmar();
        turnoDAO.actualizar(turno);
        turnoEnHoldId = null;
        return turno;
    }

    @Override
    public Turno getTurnoEnHold() {
        return turnoEnHoldId == null ? null : turnoDAO.buscarPorId(turnoEnHoldId);
    }

    @Override
    public List<Turno> buscarConfirmadosDePaciente(Long pacienteId) {
        return turnoDAO.buscarConfirmadosDePaciente(pacienteId);
    }

    @Override
    public List<Turno> buscarConfirmadosDeProfesional(Long profesionalId) {
        return turnoDAO.buscarConfirmadosDeProfesional(profesionalId);
    }

    @Override
    public Turno cancelar(Long turnoId, Long pacienteId) {
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.CONFIRMADO
                || turno.getPaciente() == null || !turno.getPaciente().getId().equals(pacienteId)) {
            throw new TurnoNoDisponibleException("El turno no existe, no está confirmado o no te pertenece.");
        }
        turno.liberar();
        turnoDAO.actualizar(turno);
        return turno;
    }

    @PrePassivate
    void antesDePasivar() {
        LOG.info("Pasivando conversación de reserva (turno en hold: " + turnoEnHoldId + ").");
    }

    @PostActivate
    void despuesDeActivar() {
        LOG.info("Reactivando conversación de reserva (turno en hold: " + turnoEnHoldId + ").");
    }

    @PreDestroy
    void liberarHoldSiQuedoPendiente() {
        if (turnoEnHoldId != null) {
            Turno turno = turnoDAO.buscarPorId(turnoEnHoldId);
            if (turno != null && turno.getEstado() == EstadoTurno.HOLD) {
                turno.liberar();
                turnoDAO.actualizar(turno);
                LOG.warning("Hold del turno " + turnoEnHoldId
                        + " expiró sin confirmación; el contenedor liberó el turno automáticamente.");
            }
        }
    }
}
