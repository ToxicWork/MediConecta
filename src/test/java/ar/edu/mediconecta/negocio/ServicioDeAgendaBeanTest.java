package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.ejb.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La agenda que ven la web y la API: un turno sólo se le muestra a su paciente o a su profesional (IDOR). */
class ServicioDeAgendaBeanTest {

    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final TurnoDAO turnoDAO = mock(TurnoDAO.class);
    private final SessionContext contexto = mock(SessionContext.class);
    private final Paciente paciente = Fixtures.paciente(7L, "OSDE");
    private ServicioDeAgendaBean agenda;
    private Turno turno;

    @BeforeEach
    void armar() {
        agenda = new ServicioDeAgendaBean();
        Inyectar.en(agenda, "usuarioDAO", usuarioDAO);
        Inyectar.en(agenda, "turnoDAO", turnoDAO);
        Inyectar.en(agenda, "contexto", contexto);
        turno = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(1)), 10L);
        turno.reservar(paciente, LocalDateTime.now().plusMinutes(5));
        when(turnoDAO.buscarPorId(10L)).thenReturn(turno);
    }

    private void comoUsuario(String email, Object usuario) {
        when(contexto.getCallerPrincipal()).thenReturn(() -> email);
        when(usuarioDAO.buscarPorEmail(email)).thenReturn(Optional.ofNullable((ar.edu.mediconecta.modelo.Usuario) usuario));
    }

    @Test
    void unTurnoSeLeMuestraASuPacienteYASuProfesional() {
        comoUsuario(paciente.getEmail(), paciente);
        assertSame(turno, agenda.turnoDelUsuarioActual(10L).orElseThrow());

        Profesional ana = turno.getProfesional();
        comoUsuario(ana.getEmail(), ana);
        assertSame(turno, agenda.turnoDelUsuarioActual(10L).orElseThrow());
    }

    @Test
    void unTurnoAjenoOInexistenteNoSeMuestra() {
        Paciente otro = Fixtures.paciente(8L, "OSDE");
        comoUsuario("otro@test", otro);
        assertTrue(agenda.turnoDelUsuarioActual(10L).isEmpty());
        assertTrue(agenda.turnoDelUsuarioActual(999L).isEmpty());

        comoUsuario("nadie@test", null);
        assertTrue(agenda.turnoDelUsuarioActual(10L).isEmpty());
        assertEquals(List.of(), agenda.turnosDelUsuarioActual());
    }

    @Test
    void losTurnosDelUsuarioSonLosDeSuId() {
        comoUsuario(paciente.getEmail(), paciente);
        when(turnoDAO.buscarDeUsuario(7L)).thenReturn(List.of(turno));

        assertEquals(List.of(turno), agenda.turnosDelUsuarioActual());
    }

    @Test
    void elListadoPublicoEsElDeProfesionalesYSuDisponibilidad() {
        List<Profesional> profesionales = List.of(turno.getProfesional());
        when(usuarioDAO.listarProfesionales()).thenReturn(profesionales);
        when(turnoDAO.buscarDisponibles(20L)).thenReturn(List.of(turno));

        assertEquals(profesionales, agenda.profesionales());
        assertEquals(List.of(turno), agenda.disponibilidadDe(20L));
    }

    /** La pantalla Turnos del administrativo pagina de a 25 como máximo, y un desde negativo empieza en cero. */
    @Test
    void laPaginacionDelAdministrativoSeAcotaA25PorPagina() {
        agenda.proximosConfirmados(20L, -5, 100);
        agenda.proximosConfirmados(20L, 50, 0);
        when(turnoDAO.contarProximosConfirmados(eq(20L), any())).thenReturn(42);

        verify(turnoDAO).buscarProximosConfirmados(eq(20L), any(), eq(0), eq(25));
        verify(turnoDAO).buscarProximosConfirmados(eq(20L), any(), eq(50), eq(1));
        assertEquals(42, agenda.contarProximosConfirmados(20L));
    }
}
