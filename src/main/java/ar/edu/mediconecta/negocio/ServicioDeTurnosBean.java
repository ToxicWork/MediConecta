package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
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

    private static final DateTimeFormatter FORMATO_HORARIO = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm");

    private static final Logger LOG = Logger.getLogger(ServicioDeTurnosBean.class.getName());
    static final Duration DURACION_HOLD = Duration.ofMinutes(5);
    /** Los horarios van de 5 en 5 minutos: el mismo paso que el selector de fecha de la Agenda (D-39). */
    static final int PASO_EN_MINUTOS = 5;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private ProcesoConfirmacionTurno procesoConfirmacion;

    @Inject
    private ProcesoCancelacionTurno procesoCancelacion;

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
        // D-39: el selector redondea a 5 minutos sólo a veces (según cuándo procesa lo escrito); el servidor lo exige
        // siempre, así un mismo horario escrito no puede quedar publicado de dos maneras.
        if (fechaHora.getMinute() % PASO_EN_MINUTOS != 0 || fechaHora.getSecond() != 0 || fechaHora.getNano() != 0) {
            LocalDateTime anterior = fechaHora.withSecond(0).withNano(0).minusMinutes(fechaHora.getMinute() % PASO_EN_MINUTOS);
            throw new TurnoNoDisponibleException("Los horarios van de " + PASO_EN_MINUTOS + " en " + PASO_EN_MINUTOS
                    + " minutos: elegí las " + FORMATO_HORA.format(anterior) + " o las "
                    + FORMATO_HORA.format(anterior.plusMinutes(PASO_EN_MINUTOS)) + ".");
        }
        // D-23: con la fila del profesional bloqueada, dos publicaciones simultáneas (doble clic) no pasan las dos
        // la verificación: la segunda espera el commit de la primera y ve el horario ya publicado.
        usuarioDAO.bloquear(profesionalId);
        if (turnoDAO.profesionalTieneHorarioEn(profesionalId, fechaHora)) {
            throw new TurnoNoDisponibleException("Ya tenés publicado un horario el "
                    + FORMATO_HORARIO.format(fechaHora) + " hs.");
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
        // D-36: si dos pacientes reservan el mismo horario a la vez, el segundo espera y la cadena de validación le
        // dice que ya no está disponible (antes recibía un error 500 al hacer el commit).
        Turno turno = turnoId == null ? null : turnoDAO.buscarPorIdParaActualizar(turnoId);
        validaciones.paraReservar().validar(new SolicitudReserva(turno, pacienteId, ahora)); // Chain of Responsibility
        // TurnoExistente, el primer eslabón de la cadena, ya rechazó un turno inexistente: acá nunca es null.
        Objects.requireNonNull(turno, "turno validado");
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
            Turno turno = turnoDAO.buscarPorIdParaActualizar(turnoEnHoldId);
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
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public List<Turno> buscarProximosConfirmados(Long usuarioId) {
        return turnoDAO.buscarProximosConfirmadosDeUsuario(usuarioId, LocalDateTime.now());
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    public List<Turno> buscarConfirmadosDeProfesional(Long profesionalId) {
        return turnoDAO.buscarConfirmadosDeProfesional(profesionalId);
    }

    /**
     * Delega en {@link ProcesoCancelacionTurno}, que es sin estado y también atiende a la API REST y a la pantalla
     * Turnos del administrativo. El paciente cancela sus turnos y el profesional los de su agenda.
     */
    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public Turno cancelar(Long turnoId) {
        return procesoCancelacion.cancelar(turnoId);
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

    /**
     * D-33: el contenedor llama a este callback sin transacción (para un bean con estado, por defecto
     * NOT_SUPPORTED), y actualizar el turno la necesita: sin REQUIRES_NEW fallaba siempre (WFLYEE0006) y el turno
     * lo terminaba liberando {@link LiberadorDeHolds}. EJB 3.2 admite REQUIRES_NEW en los callbacks de ciclo de vida
     * de un bean con estado.
     */
    @PreDestroy
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    void liberarHoldSiQuedoPendiente() {
        if (turnoEnHoldId != null) {
            Turno turno = turnoDAO.buscarPorIdParaActualizar(turnoEnHoldId);
            if (turno != null && turno.getEstado() == EstadoTurno.HOLD) {
                turno.liberar();
                turnoDAO.actualizar(turno);
                LOG.warning("Hold del turno " + turnoEnHoldId
                        + " expiró sin confirmación; el contenedor liberó el turno automáticamente.");
            }
        }
    }
}
