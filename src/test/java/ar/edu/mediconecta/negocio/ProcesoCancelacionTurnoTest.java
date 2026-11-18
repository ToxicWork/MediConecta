package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.mensajeria.PublicadorEventosTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.Administrativo;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.TransicionInvalidaException;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.PrecondicionFallidaException;
import ar.edu.mediconecta.negocio.excepciones.TurnoInexistenteException;
import jakarta.ejb.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cancelación de un turno (también por la API): titularidad por rol, precondición If-Match (412), patrón State (409),
 * el horario que se vuelve a ofrecer sólo si cancela el paciente y el evento para el reintegro.
 */
class ProcesoCancelacionTurnoTest {

    private static final long TURNO = 10L;

    private final TurnoDAO turnoDAO = mock(TurnoDAO.class);
    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final PublicadorEventosTurno publicador = mock(PublicadorEventosTurno.class);
    private final SessionContext contexto = mock(SessionContext.class);
    private final Paciente paciente = Fixtures.paciente(7L, "OSDE");
    private ProcesoCancelacionTurno proceso;
    private Turno turno;

    @BeforeEach
    void armar() {
        proceso = new ProcesoCancelacionTurno();
        Inyectar.en(proceso, "turnoDAO", turnoDAO);
        Inyectar.en(proceso, "usuarioDAO", usuarioDAO);
        Inyectar.en(proceso, "publicadorEventos", publicador);
        Inyectar.en(proceso, "contexto", contexto);
        turno = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(2)), TURNO);
        turno.reservar(paciente, LocalDateTime.now().plusMinutes(5));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.PARTICULAR, null, null, "420101", new BigDecimal("32000"),
                new BigDecimal("32000"), BigDecimal.ZERO, EstadoAutorizacion.NO_REQUIERE));
        when(turnoDAO.buscarPorIdParaActualizar(TURNO)).thenReturn(turno);
    }

    private void comoUsuario(Usuario usuario) {
        Principal principal = usuario::getEmail;
        when(contexto.getCallerPrincipal()).thenReturn(principal);
        when(usuarioDAO.buscarPorEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    }

    @Test
    void elPacienteCancelaSuTurnoYElHorarioVuelveAOfrecerse() {
        comoUsuario(paciente);

        proceso.cancelar(TURNO);

        assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        assertEquals(Rol.PACIENTE, turno.getCanceladoPor());
        verify(turnoDAO).guardar(argThat(nuevo -> nuevo.getEstado() == EstadoTurno.DISPONIBLE
                && nuevo.getFechaHora().equals(turno.getFechaHora())));
        verify(publicador).publicar(argThat(e -> e.tipo() == TipoEventoTurno.TURNO_CANCELADO && e.turnoId() == TURNO));
    }

    /** Si cancela el profesional, normalmente es porque no puede atender: el horario no se vuelve a publicar. */
    @Test
    void elProfesionalCancelaUnTurnoDeSuAgendaSinVolverAPublicarElHorario() {
        comoUsuario(turno.getProfesional());

        proceso.cancelar(TURNO);

        assertEquals(Rol.PROFESIONAL, turno.getCanceladoPor());
        verify(turnoDAO, never()).guardar(any());
        verify(publicador).publicar(any());
    }

    @Test
    void elAdministrativoCancelaCualquierTurno() {
        comoUsuario(Fixtures.conId(new Administrativo("Carla", "carla@test", "x"), 30L));

        proceso.cancelar(TURNO);

        assertEquals(Rol.ADMINISTRATIVO, turno.getCanceladoPor());
    }

    /** Un turno ajeno se informa igual que uno inexistente (404), para no revelar ids. */
    @Test
    void unTurnoAjenoEsComoUnoInexistente() {
        comoUsuario(Fixtures.paciente(8L, "OSDE"));

        assertThrows(TurnoInexistenteException.class, () -> proceso.cancelar(TURNO));
        assertThrows(TurnoInexistenteException.class, () -> proceso.cancelar(999L));

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        verify(publicador, never()).publicar(any());
    }

    /** Eje C: con una ETag vieja (otra versión del turno) no se cancela: 412. Con la vigente, sí. */
    @Test
    void conUnaVersionViejaNoSeCancelaYConLaVigenteSi() {
        comoUsuario(paciente);

        assertThrows(PrecondicionFallidaException.class, () -> proceso.cancelar(TURNO, Set.of(turno.getVersion() + 1)));
        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());

        proceso.cancelar(TURNO, Set.of(turno.getVersion()));
        assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
    }

    /** Un turno ajeno da 404 aunque la versión no coincida: la precondición se mira después de la titularidad. */
    @Test
    void laTitularidadSeVerificaAntesQueLaPrecondicion() {
        comoUsuario(Fixtures.paciente(8L, "OSDE"));

        assertThrows(TurnoInexistenteException.class, () -> proceso.cancelar(TURNO, Set.of(-1)));
    }

    @Test
    void cancelarDosVecesEsUnaTransicionInvalida() {
        comoUsuario(paciente);
        proceso.cancelar(TURNO);

        assertThrows(TransicionInvalidaException.class, () -> proceso.cancelar(TURNO));
        verify(publicador).publicar(any()); // un solo evento: un solo reintegro
    }
}
