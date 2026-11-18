package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.HistoriaClinicaDAO;
import ar.edu.mediconecta.dao.RegistroAccesoDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EntradaClinica;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.RegistroAcceso;
import ar.edu.mediconecta.modelo.TipoEntrada;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.dto.AccesoHistoriaDTO;
import ar.edu.mediconecta.negocio.excepciones.AccesoDenegadoException;
import jakarta.ejb.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Historia clínica (SEC-D01): el paciente ve sólo la suya; el profesional, la de un paciente con el que tiene o tuvo un
 * turno confirmado, y sólo él agrega entradas, asociadas a un turno suyo con ese paciente.
 */
class ServicioDeHistoriaClinicaBeanTest {

    private final HistoriaClinicaDAO historias = mock(HistoriaClinicaDAO.class);
    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final TurnoDAO turnoDAO = mock(TurnoDAO.class);
    private final RegistroAccesoDAO accesos = mock(RegistroAccesoDAO.class);
    private final SessionContext contexto = mock(SessionContext.class);
    private final Paciente paciente = Fixtures.paciente(7L, "OSDE");
    private final Profesional profesional = Fixtures.profesional(20L, "Cardiología");
    private ServicioDeHistoriaClinicaBean servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioDeHistoriaClinicaBean();
        Inyectar.en(servicio, "historiaClinicaDAO", historias);
        Inyectar.en(servicio, "usuarioDAO", usuarioDAO);
        Inyectar.en(servicio, "turnoDAO", turnoDAO);
        Inyectar.en(servicio, "registroAccesoDAO", accesos);
        Inyectar.en(servicio, "contexto", contexto);
        when(usuarioDAO.obtenerReferenciaPaciente(7L)).thenReturn(paciente);
    }

    private void comoUsuario(Usuario usuario) {
        when(contexto.getCallerPrincipal()).thenReturn(usuario::getEmail);
        when(usuarioDAO.buscarPorEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    }

    private Turno confirmadoConElPaciente(long id) {
        Turno turno = Fixtures.conId(Fixtures.turno(LocalDateTime.now().minusDays(1)), id);
        turno.reservar(paciente, LocalDateTime.now().plusMinutes(5));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.PARTICULAR, null, null, "420101", new BigDecimal("32000"),
                new BigDecimal("32000"), BigDecimal.ZERO, EstadoAutorizacion.NO_REQUIERE));
        when(turnoDAO.buscarPorId(id)).thenReturn(turno);
        return turno;
    }

    @Test
    void elPacienteVeSuHistoriaYSiNoTieneSeLeCrea() {
        comoUsuario(paciente);

        HistoriaClinica historia = servicio.obtenerDePaciente(7L);

        assertSame(paciente, historia.getPaciente());
        verify(historias).guardar(historia);
    }

    @Test
    void elPacienteNoVeLaHistoriaDeOtro() {
        comoUsuario(paciente);

        assertThrows(AccesoDenegadoException.class, () -> servicio.obtenerDePaciente(8L));
        verify(historias, never()).buscarPorPaciente(any());
    }

    @Test
    void elProfesionalSinTurnoConfirmadoConElPacienteNoVeSuHistoria() {
        comoUsuario(profesional);
        when(turnoDAO.existeConfirmadoEntre(20L, 7L)).thenReturn(false);

        assertThrows(AccesoDenegadoException.class, () -> servicio.obtenerDePaciente(7L));
    }

    @Test
    void elProfesionalConTurnoConfirmadoVeLaHistoriaExistenteYAgregaUnDiagnostico() {
        comoUsuario(profesional);
        when(turnoDAO.existeConfirmadoEntre(20L, 7L)).thenReturn(true);
        HistoriaClinica existente = new HistoriaClinica(paciente);
        when(historias.buscarPorPaciente(7L)).thenReturn(existente);
        Turno turno = confirmadoConElPaciente(50L);

        assertSame(existente, servicio.obtenerDePaciente(7L));
        EntradaClinica entrada = servicio.agregarEntrada(7L, 50L, TipoEntrada.DIAGNOSTICO, "Hipertensión leve");

        assertEquals("Hipertensión leve", entrada.getDescripcion());
        assertSame(profesional, entrada.getProfesional());
        assertSame(turno, entrada.getTurno());
        assertEquals(List.of(entrada), existente.getEntradas());
        verify(historias).agregarEntrada(entrada);
        verify(historias, never()).guardar(any());
    }

    /** SEC-D01: la entrada no se puede asociar a un turno que no es del profesional con ese paciente. */
    @Test
    void laEntradaSoloSeAsociaAUnTurnoConfirmadoDelProfesionalConElPaciente() {
        comoUsuario(profesional);
        when(turnoDAO.existeConfirmadoEntre(20L, 7L)).thenReturn(true);
        Turno ajeno = Fixtures.conId(new Turno(Fixtures.profesional(21L, "Dermatología"), LocalDateTime.now()), 51L);
        when(turnoDAO.buscarPorId(51L)).thenReturn(ajeno);

        assertThrows(AccesoDenegadoException.class,
                () -> servicio.agregarEntrada(7L, 51L, TipoEntrada.RECETA, "Enalapril 10 mg"));
        assertThrows(AccesoDenegadoException.class,
                () -> servicio.agregarEntrada(7L, 999L, TipoEntrada.RECETA, "Enalapril 10 mg"));
        verify(historias, never()).agregarEntrada(any());
    }

    @Test
    void sinUsuarioIdentificadoNoHayAcceso() {
        when(contexto.getCallerPrincipal()).thenReturn(() -> "nadie@mediconecta.test");

        assertThrows(AccesoDenegadoException.class, () -> servicio.obtenerDePaciente(7L));
    }

    /** El paciente ve quién consultó o modificó su historia, sin sus propios accesos. */
    @Test
    void elPacienteVeQuienAccedioASuHistoria() {
        comoUsuario(paciente);
        RegistroAcceso consulta = new RegistroAcceso("ana@test", "obtenerDePaciente", "historia_clinica", 7L, "PERMITIDO", null);
        RegistroAcceso entrada = new RegistroAcceso("ana@test", "agregarEntrada", "historia_clinica", 7L, "PERMITIDO", null);
        when(accesos.buscarDeRecursoConNombre("historia_clinica", 7L, paciente.getEmail(), 15))
                .thenReturn(List.of(new Object[] {consulta, "Dra. Ana Lopez"}, new Object[] {entrada, "Dra. Ana Lopez"}));

        List<AccesoHistoriaDTO> vistos = servicio.accesosAMiHistoria();

        assertEquals(2, vistos.size());
        assertEquals("Dra. Ana Lopez", vistos.get(0).getQuien());
        assertEquals("Consultó la historia", vistos.get(0).getOperacion());
        assertEquals("Agregó una entrada", vistos.get(1).getOperacion());
        assertEquals("PERMITIDO", vistos.get(1).getResultado());
    }
}
