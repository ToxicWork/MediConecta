package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.excepciones.ConfirmacionFallidaException;
import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;
import ar.edu.mediconecta.negocio.excepciones.TurnoNoDisponibleException;
import ar.edu.mediconecta.negocio.validacion.CadenaDeValidacion;
import jakarta.ejb.EJBException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La conversación de reserva (componente con estado): publicar horarios (D-23), el hold de 5 minutos, la confirmación
 * que delega en el proceso transaccional y la liberación, también la que hace el contenedor al vencer (D-33). Con la
 * cadena de validación real (Chain of Responsibility) y los DAO simulados.
 */
class ServicioDeTurnosBeanTest {

    private static final long PROFESIONAL = 20L;
    private static final long PACIENTE = 7L;

    private final TurnoDAO turnoDAO = mock(TurnoDAO.class);
    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final ProcesoConfirmacionTurno confirmacion = mock(ProcesoConfirmacionTurno.class);
    private final ProcesoCancelacionTurno cancelacion = mock(ProcesoCancelacionTurno.class);
    private final Paciente paciente = Fixtures.paciente(PACIENTE, "OSDE");
    private ServicioDeTurnosBean servicio;

    @BeforeEach
    void armar() {
        CadenaDeValidacion validaciones = Inyectar.en(new CadenaDeValidacion(), "turnoDAO", turnoDAO);
        Inyectar.invocar(validaciones, "armar");
        servicio = new ServicioDeTurnosBean();
        Inyectar.en(servicio, "turnoDAO", turnoDAO);
        Inyectar.en(servicio, "usuarioDAO", usuarioDAO);
        Inyectar.en(servicio, "procesoConfirmacion", confirmacion);
        Inyectar.en(servicio, "procesoCancelacion", cancelacion);
        Inyectar.en(servicio, "validaciones", validaciones);
        when(usuarioDAO.obtenerReferenciaPaciente(PACIENTE)).thenReturn(paciente);
    }

    private Turno libre(long id) {
        Turno turno = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(2)), id);
        when(turnoDAO.buscarPorIdParaActualizar(id)).thenReturn(turno);
        when(turnoDAO.buscarPorId(id)).thenReturn(turno);
        return turno;
    }

    // ---- Publicar disponibilidad ------------------------------------------------------------------

    @Test
    void unHorarioQueYaPasoNoSePublica() {
        TurnoNoDisponibleException error = assertThrows(TurnoNoDisponibleException.class,
                () -> servicio.publicarDisponibilidad(PROFESIONAL, LocalDateTime.now().minusMinutes(1), Modalidad.PRESENCIAL));

        assertEquals("Elegí una fecha y hora futura.", error.getMessage());
        verify(turnoDAO, never()).guardar(any());
    }

    /** D-23: con la fila del profesional bloqueada, un horario repetido se rechaza con el horario en el mensaje. */
    @Test
    void unHorarioRepetidoSeRechazaYDiceCual() {
        LocalDateTime hora = LocalDateTime.now().plusDays(3).withHour(10).withMinute(30).withSecond(0).withNano(0);
        when(turnoDAO.profesionalTieneHorarioEn(PROFESIONAL, hora)).thenReturn(true);

        TurnoNoDisponibleException error = assertThrows(TurnoNoDisponibleException.class,
                () -> servicio.publicarDisponibilidad(PROFESIONAL, hora, Modalidad.PRESENCIAL));

        assertTrue(error.getMessage().startsWith("Ya tenés publicado un horario el "), error.getMessage());
        assertTrue(error.getMessage().endsWith(" a las 10:30 hs."), error.getMessage());
        verify(usuarioDAO).bloquear(PROFESIONAL);
        verify(turnoDAO, never()).guardar(any());
    }

    @Test
    void unHorarioNuevoSeGuardaDisponibleConSuModalidad() {
        LocalDateTime hora = LocalDateTime.now().plusDays(3);
        when(usuarioDAO.obtenerReferenciaProfesional(PROFESIONAL)).thenReturn(Fixtures.profesional(PROFESIONAL, "Cardiología"));

        Turno publicado = servicio.publicarDisponibilidad(PROFESIONAL, hora, Modalidad.TELEMEDICINA);

        assertEquals(EstadoTurno.DISPONIBLE, publicado.getEstado());
        assertEquals(hora, publicado.getFechaHora());
        assertTrue(publicado.isTelemedicina());
        verify(turnoDAO).guardar(publicado);
    }

    // ---- Hold --------------------------------------------------------------------------------------

    @Test
    void reservarUnTurnoLibreLoRetieneCincoMinutosParaElPaciente() {
        Turno turno = libre(10L);

        servicio.reservarHold(10L, PACIENTE);

        assertEquals(EstadoTurno.HOLD, turno.getEstado());
        assertTrue(turno.perteneceAPaciente(PACIENTE));
        ZoneId zona = ZoneId.systemDefault();
        Duration vence = Duration.between(LocalDateTime.now().atZone(zona), turno.getHoldVenceEn().atZone(zona));
        assertTrue(vence.compareTo(Duration.ofMinutes(4)) > 0 && vence.compareTo(Duration.ofMinutes(5)) <= 0, vence.toString());
        assertSame(turno, servicio.getTurnoEnHold());
        verify(turnoDAO).actualizar(turno);
    }

    @Test
    void conUnTurnoEnEsperaNoSePuedeReservarOtro() {
        libre(10L);
        libre(11L);
        servicio.reservarHold(10L, PACIENTE);

        TurnoNoDisponibleException error = assertThrows(TurnoNoDisponibleException.class, () -> servicio.reservarHold(11L, PACIENTE));

        assertTrue(error.getMessage().startsWith("Ya tenés un turno en espera"), error.getMessage());
    }

    @Test
    void unTurnoQueOtroPacienteRetieneNoSeReserva() {
        Turno turno = libre(10L);
        turno.reservar(Fixtures.paciente(8L, "OSDE"), LocalDateTime.now().plusMinutes(5));

        ReservaInvalidaException error = assertThrows(ReservaInvalidaException.class, () -> servicio.reservarHold(10L, PACIENTE));

        assertEquals("El turno ya no está disponible.", error.getMessage());
        assertNull(servicio.getTurnoEnHold());
    }

    @Test
    void unTurnoInexistenteNoSeReserva() {
        ReservaInvalidaException error = assertThrows(ReservaInvalidaException.class, () -> servicio.reservarHold(99L, PACIENTE));

        assertEquals("El turno no existe.", error.getMessage());
    }

    @Test
    void siElTemporizadorLiberoElHoldLaConversacionLoOlvida() {
        Turno turno = libre(10L);
        servicio.reservarHold(10L, PACIENTE);

        turno.liberar(); // lo hizo LiberadorDeHolds

        assertNull(servicio.getTurnoEnHold());
        TurnoNoDisponibleException error = assertThrows(TurnoNoDisponibleException.class, () -> servicio.confirmar(null));
        assertTrue(error.getMessage().contains("5 minutos"), error.getMessage());
    }

    @Test
    void liberarElHoldDevuelveElTurnoALaAgenda() {
        Turno turno = libre(10L);
        servicio.reservarHold(10L, PACIENTE);

        servicio.liberarHold();

        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());
        assertNull(servicio.getTurnoEnHold());
    }

    /** D-33: al vencer la conversación, el contenedor llama al @PreDestroy y el turno vuelve a ofrecerse. */
    @Test
    void alDestruirseLaConversacionSeLiberaElHoldPendiente() {
        Turno turno = libre(10L);
        servicio.reservarHold(10L, PACIENTE);

        servicio.liberarHoldSiQuedoPendiente();

        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());
    }

    @Test
    void alDestruirseSinHoldNoTocaNada() {
        servicio.liberarHoldSiQuedoPendiente();

        verify(turnoDAO, never()).buscarPorIdParaActualizar(any());
    }

    // ---- Cotizar y confirmar ----------------------------------------------------------------------

    @Test
    void sinTurnoEnEsperaNoHayNadaQueCotizarNiConfirmar() {
        assertThrows(TurnoNoDisponibleException.class, () -> servicio.cotizarTurnoEnHold());
        assertThrows(TurnoNoDisponibleException.class, () -> servicio.confirmar(null));
        verify(confirmacion, never()).confirmar(any(), any(), any());
    }

    @Test
    void cotizarYConfirmarDelegaEnElProcesoYDespuesOlvidaElHold() {
        Turno turno = libre(10L);
        servicio.reservarHold(10L, PACIENTE);
        servicio.cotizarTurnoEnHold();
        when(confirmacion.confirmar(eq(10L), eq(PACIENTE), any())).thenReturn(turno);

        assertSame(turno, servicio.confirmar(null));

        verify(confirmacion).cotizar(10L);
        assertNull(Inyectar.leer(servicio, "turnoEnHoldId"));
    }

    /** Una falla de sistema al confirmar ya se revirtió en el proceso: el paciente conserva el hold para reintentar. */
    @Test
    void unaFallaDeSistemaAlConfirmarConservaElHoldParaReintentar() {
        Turno turno = libre(10L);
        servicio.reservarHold(10L, PACIENTE);
        when(confirmacion.confirmar(eq(10L), eq(PACIENTE), any())).thenThrow(new EJBException("la base se cayó"));

        assertThrows(ConfirmacionFallidaException.class, () -> servicio.confirmar(null));

        assertSame(turno, servicio.getTurnoEnHold());
    }

    // ---- Consultas y cancelación ------------------------------------------------------------------

    @Test
    void lasConsultasPidenLoQueCorrespondeAlDao() {
        List<Turno> disponibles = List.of(Fixtures.turno(LocalDateTime.now().plusDays(1)));
        when(turnoDAO.buscarDisponibles(PROFESIONAL)).thenReturn(disponibles);
        when(turnoDAO.buscarConfirmadosDeProfesional(PROFESIONAL)).thenReturn(List.of());
        LocalDateTime antes = LocalDateTime.now();

        assertSame(disponibles, servicio.buscarDisponibilidad(PROFESIONAL));
        assertEquals(List.of(), servicio.buscarConfirmadosDeProfesional(PROFESIONAL));
        servicio.buscarProximosConfirmados(PACIENTE);

        verify(turnoDAO).buscarProximosConfirmadosDeUsuario(eq(PACIENTE),
                org.mockito.ArgumentMatchers.argThat(ahora -> !ahora.isBefore(antes) && !ahora.isAfter(LocalDateTime.now())));
    }

    @Test
    void cancelarDelegaEnElProcesoDeCancelacion() {
        Turno cancelado = Fixtures.turno(LocalDateTime.now().plusDays(1));
        when(cancelacion.cancelar(5L)).thenReturn(cancelado);

        assertSame(cancelado, servicio.cancelar(5L));
    }
}
