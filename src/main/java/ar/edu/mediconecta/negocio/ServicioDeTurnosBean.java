package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.PublicadorEventosTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.dto.CotizacionTurnoDTO;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import ar.edu.mediconecta.negocio.excepciones.ConfirmacionFallidaException;
import ar.edu.mediconecta.negocio.excepciones.TurnoNoDisponibleException;
import ar.edu.mediconecta.negocio.validacion.CadenaDeValidacion;
import ar.edu.mediconecta.negocio.validacion.SolicitudReserva;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.EJBException;
import jakarta.ejb.PostActivate;
import jakarta.ejb.PrePassivate;
import jakarta.ejb.Stateful;
import jakarta.ejb.StatefulTimeout;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fachada (patrón Facade) del subproceso de reserva de turnos: agrupa búsqueda de disponibilidad, hold
 * temporal, cotización y confirmación detrás de una interfaz simple.
 *
 * Es {@code @Stateful} porque el hold de un turno (~5 min) es conversacional: vive atado a la sesión del
 * paciente mientras decide confirmar. El {@code @StatefulTimeout} hace que el propio contenedor EJB invoque
 * {@code @PreDestroy} si el paciente no confirma a tiempo, liberando el turno automáticamente.
 *
 * <p>La confirmación delega en {@link ProcesoConfirmacionTurno}, que es la frontera transaccional del flujo
 * crítico. Por eso {@link #confirmar} y {@link #cotizarTurnoEnHold} son {@code NOT_SUPPORTED}: la
 * transacción no la abre la conversación sino el proceso, y si falla, ya está revertida cuando vuelve acá.
 */
@Stateful
@StatefulTimeout(value = 5, unit = TimeUnit.MINUTES)
@DeclareRoles({"PACIENTE", "PROFESIONAL"})
public class ServicioDeTurnosBean implements ServicioDeTurnos {

    private static final Logger LOG = Logger.getLogger(ServicioDeTurnosBean.class.getName());
    static final Duration DURACION_HOLD = Duration.ofMinutes(5);

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private PublicadorEventosTurno publicadorEventos;

    @Inject
    private ProcesoConfirmacionTurno procesoConfirmacion;

    @Inject
    private CadenaDeValidacion validaciones;

    private Long turnoEnHoldId;
    private Long pacienteDelHoldId;

    @PostConstruct
    void iniciarConversacion() {
        LOG.info("Conversación de reserva de turno iniciada por el contenedor.");
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    public Turno publicarDisponibilidad(Long profesionalId, LocalDateTime fechaHora, Modalidad modalidad) {
        if (fechaHora == null || !fechaHora.isAfter(LocalDateTime.now())) {
            throw new TurnoNoDisponibleException("Elegí una fecha y hora futura.");
        }
        Turno turno = new Turno(usuarioDAO.obtenerReferenciaProfesional(profesionalId), fechaHora, modalidad);
        turnoDAO.guardar(turno);
        return turno;
    }

    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public List<Turno> buscarDisponibilidad(Long profesionalId) {
        return turnoDAO.buscarDisponibles(profesionalId);
    }

    @Override
    @RolesAllowed("PACIENTE")
    public Turno reservarHold(Long turnoId, Long pacienteId) {
        descartarHoldQueYaNoExiste();
        if (turnoEnHoldId != null) {
            throw new TurnoNoDisponibleException("Ya tenés un turno en espera: confirmalo o liberalo antes de reservar otro.");
        }
        LocalDateTime ahora = LocalDateTime.now();
        Turno turno = turnoDAO.buscarPorId(turnoId);
        validaciones.paraReservar().validar(new SolicitudReserva(turno, pacienteId, ahora)); // Chain of Responsibility
        turno.reservar(usuarioDAO.obtenerReferenciaPaciente(pacienteId), ahora.plus(DURACION_HOLD)); // State
        turnoDAO.actualizar(turno);
        turnoEnHoldId = turno.getId();
        pacienteDelHoldId = pacienteId;
        return turno;
    }

    @Override
    @RolesAllowed("PACIENTE")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public CotizacionTurnoDTO cotizarTurnoEnHold() {
        exigirHold();
        return procesoConfirmacion.cotizar(turnoEnHoldId);
    }

    @Override
    @RolesAllowed("PACIENTE")
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public Turno confirmar(DatosPago datosPago) {
        exigirHold();
        try {
            Turno confirmado = procesoConfirmacion.confirmar(turnoEnHoldId, pacienteDelHoldId, datosPago);
            turnoEnHoldId = null;
            pacienteDelHoldId = null;
            return confirmado;
        } catch (EJBException falla) {
            // Error de sistema dentro del proceso: su transacción ya se revirtió (y el cobro, si lo hubo, ya se
            // compensó). Se traduce a una excepción de aplicación para que el contenedor no destruya esta
            // instancia y el paciente conserve el hold para reintentar.
            LOG.log(Level.SEVERE, "Falla de sistema al confirmar el turno " + turnoEnHoldId, falla);
            throw new ConfirmacionFallidaException();
        }
    }

    @Override
    @RolesAllowed("PACIENTE")
    public void liberarHold() {
        if (turnoEnHoldId != null) {
            Turno turno = turnoDAO.buscarPorId(turnoEnHoldId);
            if (turno != null && turno.getEstado() == EstadoTurno.HOLD && turno.perteneceAPaciente(pacienteDelHoldId)) {
                turno.liberar();
                turnoDAO.actualizar(turno);
            }
        }
        turnoEnHoldId = null;
        pacienteDelHoldId = null;
    }

    @Override
    @RolesAllowed("PACIENTE")
    public Turno getTurnoEnHold() {
        descartarHoldQueYaNoExiste();
        return turnoEnHoldId == null ? null : turnoDAO.buscarPorId(turnoEnHoldId);
    }

    @Override
    @RolesAllowed("PACIENTE")
    public List<Turno> buscarConfirmadosDePaciente(Long pacienteId) {
        return turnoDAO.buscarConfirmadosDePaciente(pacienteId);
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    public List<Turno> buscarConfirmadosDeProfesional(Long profesionalId) {
        return turnoDAO.buscarConfirmadosDeProfesional(profesionalId);
    }

    /**
     * El turno cancelado queda como historial (con su pago y autorización) y el horario se vuelve a ofrecer
     * como un turno nuevo. El reembolso, si hubo cobro, lo hace Facturación al consumir TURNO_CANCELADO:
     * recién después del commit, así nunca se devuelve dinero de una cancelación que no se concretó.
     */
    @Override
    @RolesAllowed("PACIENTE")
    public Turno cancelar(Long turnoId, Long pacienteId) {
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null || turno.getEstado() != EstadoTurno.CONFIRMADO || !turno.perteneceAPaciente(pacienteId)) {
            throw new TurnoNoDisponibleException("El turno no existe, no está confirmado o no te pertenece.");
        }
        turno.cancelar(); // State: CONFIRMADO -> CANCELADO
        turnoDAO.actualizar(turno);
        if (turno.getFechaHora().isAfter(LocalDateTime.now())) {
            turnoDAO.guardar(turno.horarioLiberado());
        }
        publicadorEventos.publicar(EventoTurno.builder()
                .tipo(TipoEventoTurno.TURNO_CANCELADO)
                .turno(turno)
                .build());
        return turno;
    }

    private void exigirHold() {
        descartarHoldQueYaNoExiste();
        if (turnoEnHoldId == null) {
            throw new TurnoNoDisponibleException(
                    "No hay ningún turno en espera para confirmar (el plazo de 5 minutos pudo haber vencido).");
        }
    }

    /** Si el hold venció (lo liberó el temporizador) o lo tomó otra operación, la conversación lo olvida. */
    private void descartarHoldQueYaNoExiste() {
        if (turnoEnHoldId == null) {
            return;
        }
        Turno turno = turnoDAO.buscarPorId(turnoEnHoldId);
        if (turno == null || turno.getEstado() != EstadoTurno.HOLD || !turno.perteneceAPaciente(pacienteDelHoldId)) {
            turnoEnHoldId = null;
            pacienteDelHoldId = null;
        }
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
