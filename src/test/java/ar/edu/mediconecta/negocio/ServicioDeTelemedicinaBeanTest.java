package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.integracion.videoconsulta.SalaVideoconsulta;
import ar.edu.mediconecta.integracion.videoconsulta.VideoconsultaGateway;
import ar.edu.mediconecta.mensajeria.PublicadorEventosTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.AccesoDenegadoException;
import ar.edu.mediconecta.negocio.excepciones.VideoconsultaNoDisponibleException;
import jakarta.ejb.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La sala de videoconsulta: la abre el profesional (y se avisa al paciente) y la cierra; con el proveedor caído, avisa. */
class ServicioDeTelemedicinaBeanTest {

    private static final long TURNO = 40L;

    private final TurnoDAO turnoDAO = mock(TurnoDAO.class);
    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final VideoconsultaGateway video = mock(VideoconsultaGateway.class);
    private final PublicadorEventosTurno publicador = mock(PublicadorEventosTurno.class);
    private final SessionContext contexto = mock(SessionContext.class);
    private final Profesional profesional = Fixtures.profesional(20L, "Cardiología");
    private ServicioDeTelemedicinaBean servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioDeTelemedicinaBean();
        Inyectar.en(servicio, "turnoDAO", turnoDAO);
        Inyectar.en(servicio, "usuarioDAO", usuarioDAO);
        Inyectar.en(servicio, "video", video);
        Inyectar.en(servicio, "publicadorEventos", publicador);
        Inyectar.en(servicio, "contexto", contexto);
        comoUsuario(profesional);
        when(video.crearSala(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new SalaVideoconsulta("sala-1", "https://video/sala-1?p", "https://video/sala-1?q"));
    }

    private void comoUsuario(Usuario usuario) {
        when(contexto.getCallerPrincipal()).thenReturn(usuario::getEmail);
        when(usuarioDAO.buscarPorEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    }

    private Turno confirmado(Modalidad modalidad) {
        Turno turno = Fixtures.conId(new Turno(profesional, LocalDateTime.now().plusHours(1), modalidad), TURNO);
        turno.reservar(Fixtures.paciente(7L, "OSDE"), LocalDateTime.now().plusMinutes(5));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.PARTICULAR, null, null, "420201", new BigDecimal("30000"),
                new BigDecimal("30000"), BigDecimal.ZERO, EstadoAutorizacion.NO_REQUIERE));
        when(turnoDAO.buscarPorIdParaActualizar(TURNO)).thenReturn(turno);
        return turno;
    }

    @Test
    void iniciarAbreLaSalaYLeAvisaAlPaciente() {
        Turno turno = confirmado(Modalidad.TELEMEDICINA);

        servicio.iniciarVideoconsulta(TURNO);

        assertEquals("sala-1", turno.getSalaId());
        assertTrue(turno.isSalaAbierta());
        verify(publicador).publicar(argThat(e -> e.tipo() == TipoEventoTurno.SALA_ABIERTA));
    }

    @Test
    void siLaSalaYaEstaAbiertaNoSeCreaOtra() {
        confirmado(Modalidad.TELEMEDICINA);

        servicio.iniciarVideoconsulta(TURNO);
        servicio.iniciarVideoconsulta(TURNO);

        verify(video, times(1)).crearSala(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void unTurnoPresencialOAjenoNoTieneVideoconsulta() {
        confirmado(Modalidad.PRESENCIAL);
        assertThrows(AccesoDenegadoException.class, () -> servicio.iniciarVideoconsulta(TURNO));

        comoUsuario(Fixtures.profesional(21L, "Dermatología"));
        confirmado(Modalidad.TELEMEDICINA);
        assertThrows(AccesoDenegadoException.class, () -> servicio.iniciarVideoconsulta(TURNO));
        assertThrows(AccesoDenegadoException.class, () -> servicio.iniciarVideoconsulta(99L));
        verify(video, never()).crearSala(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void conElProveedorCaidoNoHaySalaYSeAvisaAlProfesional() {
        Turno turno = confirmado(Modalidad.TELEMEDICINA);
        when(video.crearSala(anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new IntegracionNoDisponibleException("sin respuesta", true));

        assertThrows(VideoconsultaNoDisponibleException.class, () -> servicio.iniciarVideoconsulta(TURNO));

        assertNull(turno.getSalaId());
        verify(publicador, never()).publicar(any());
    }

    /** D-14: al finalizar, la sala se cierra en el proveedor y ya no se puede volver a abrir. */
    @Test
    void finalizarCierraLaSalaYNoSePuedeVolverAAbrir() {
        Turno turno = confirmado(Modalidad.TELEMEDICINA);
        servicio.iniciarVideoconsulta(TURNO);

        servicio.finalizarVideoconsulta(TURNO);

        verify(video).cerrarSala("sala-1");
        assertNotNull(turno.getSalaCerradaEn());
        AccesoDenegadoException error = assertThrows(AccesoDenegadoException.class, () -> servicio.iniciarVideoconsulta(TURNO));
        assertEquals("La videoconsulta de este turno ya terminó.", error.getMessage());
    }

    @Test
    void noSeFinalizaUnaVideoconsultaQueNoEstaEnCursoNiConElProveedorCaido() {
        confirmado(Modalidad.TELEMEDICINA);
        assertThrows(AccesoDenegadoException.class, () -> servicio.finalizarVideoconsulta(TURNO));

        Turno turno = confirmado(Modalidad.TELEMEDICINA);
        servicio.iniciarVideoconsulta(TURNO);
        doThrow(new IntegracionNoDisponibleException("sin respuesta", true)).when(video).cerrarSala("sala-1");
        assertThrows(VideoconsultaNoDisponibleException.class, () -> servicio.finalizarVideoconsulta(TURNO));
        assertTrue(turno.isSalaAbierta(), "si el proveedor no cerró la sala, sigue abierta");
    }

    @Test
    void losTurnosVirtualesSonLosDeHoyEnAdelanteDelUsuario() {
        List<Turno> turnos = List.of(confirmado(Modalidad.TELEMEDICINA));
        when(turnoDAO.buscarVirtualesConfirmadosDeUsuario(20L, LocalDate.now().atStartOfDay())).thenReturn(turnos);

        assertEquals(turnos, servicio.turnosVirtualesDelUsuarioActual());

        when(usuarioDAO.buscarPorEmail(profesional.getEmail())).thenReturn(Optional.empty());
        assertEquals(List.of(), servicio.turnosVirtualesDelUsuarioActual());
    }
}
