package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.HistoriaClinicaDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.EntradaClinica;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.TipoEntrada;
import ar.edu.mediconecta.negocio.dto.FichaPaciente;
import ar.edu.mediconecta.negocio.excepciones.DatosDeUsuarioInvalidosException;
import jakarta.annotation.Priority;
import jakarta.annotation.security.RolesAllowed;
import jakarta.decorator.Decorator;
import jakarta.security.enterprise.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Decorator del diseño, sección 4.8: la ficha la piden profesionales y administrativos, pero los datos clínicos sólo
 * los ve el profesional que atiende al paciente (con la misma regla de turno confirmado de SEC-D01).
 */
class PacienteEnmascaradoTest {

    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final HistoriaClinicaDAO historias = mock(HistoriaClinicaDAO.class);
    private final TurnoDAO turnoDAO = mock(TurnoDAO.class);
    private final SecurityContext seguridad = mock(SecurityContext.class);
    private final Paciente paciente = Fixtures.conId(
            new Paciente("Juan Perez", "juan.perez@mediconecta.test", "x", "OSDE", "210-123456-01"), 7L);
    private final Profesional tratante = Fixtures.profesional(20L, "Cardiología");
    private final ServicioDePacientesBean servicio = new ServicioDePacientesBean();
    private final PacienteEnmascarado decorador = new PacienteEnmascarado() {
    };

    @BeforeEach
    void armar() {
        Inyectar.en(servicio, "usuarioDAO", usuarioDAO);
        Inyectar.en(servicio, "historiaClinicaDAO", historias);
        Inyectar.en(decorador, "delegado", servicio);
        Inyectar.en(decorador, "seguridad", seguridad);
        Inyectar.en(decorador, "usuarioDAO", usuarioDAO);
        Inyectar.en(decorador, "turnoDAO", turnoDAO);
        when(usuarioDAO.buscarPorId(7L)).thenReturn(paciente);
        HistoriaClinica historia = new HistoriaClinica(paciente);
        for (String texto : List.of("Hipertensión leve", "Enalapril 10 mg", "Control en 30 días", "Primera consulta")) {
            historia.agregarEntrada(new EntradaClinica(tratante, null, TipoEntrada.DIAGNOSTICO, texto));
        }
        when(historias.buscarPorPaciente(7L)).thenReturn(historia);
    }

    private void comoUsuario(String email, String rol, boolean conTurnoConfirmado) {
        when(seguridad.getCallerPrincipal()).thenReturn(() -> email);
        when(seguridad.isCallerInRole("PROFESIONAL")).thenReturn("PROFESIONAL".equals(rol));
        when(usuarioDAO.buscarPorEmail(email)).thenReturn(Optional.of(tratante));
        when(turnoDAO.existeConfirmadoEntre(20L, 7L)).thenReturn(conTurnoConfirmado);
    }

    @Test
    void elProfesionalQueLoAtiendeVeLaFichaCompleta() {
        comoUsuario("ana.lopez@mediconecta.test", "PROFESIONAL", true);

        FichaPaciente ficha = decorador.ficha(7L);

        assertTrue(ficha.isDatosClinicosVisibles());
        assertEquals(4, ficha.getTotalDeEntradas());
        assertEquals(ServicioDePacientes.ENTRADAS_EN_LA_FICHA, ficha.getUltimasEntradas().size());
        assertEquals("Hipertensión leve", ficha.getUltimasEntradas().get(0).getDescripcion());
        assertEquals("DIAGNOSTICO", ficha.getUltimasEntradas().get(0).getTipo());
        assertEquals("Dra. Ana Lopez", ficha.getUltimasEntradas().get(0).getProfesional());
        assertNotNull(ficha.getUltimasEntradas().get(0).getFecha());
        assertEquals("210-123456-01", ficha.getNroAfiliado());
    }

    @Test
    void elAdministrativoVeIdentificacionYCoberturaPeroNadaClinico() {
        comoUsuario("carla.admin@mediconecta.test", "ADMINISTRATIVO", false);

        FichaPaciente ficha = decorador.ficha(7L);

        assertFalse(ficha.isDatosClinicosVisibles());
        assertEquals(List.of(), ficha.getUltimasEntradas());
        assertEquals(0, ficha.getTotalDeEntradas(), "ni siquiera cuántas entradas tiene");
        assertEquals(7L, ficha.getPacienteId());
        assertEquals("Juan Perez", ficha.getNombreCompleto());
        assertEquals("juan.perez@mediconecta.test", ficha.getEmail());
        assertEquals("OSDE", ficha.getObraSocial());
        assertEquals("210-123456-01", ficha.getNroAfiliado(), "lo necesita para autorizaciones y reclamos");
        verify(turnoDAO, never()).existeConfirmadoEntre(20L, 7L);
    }

    @Test
    void unProfesionalSinTurnoConfirmadoConElPacienteTampocoVeLoClinico() {
        comoUsuario("pablo.ruiz@mediconecta.test", "PROFESIONAL", false);

        assertFalse(decorador.ficha(7L).isDatosClinicosVisibles());
    }

    @Test
    void sinUsuarioIdentificadoSeEnmascara() {
        when(seguridad.getCallerPrincipal()).thenReturn(null);
        when(seguridad.isCallerInRole("PROFESIONAL")).thenReturn(true);

        assertFalse(decorador.ficha(7L).isDatosClinicosVisibles());
        when(seguridad.getCallerPrincipal()).thenReturn(() -> "borrado@mediconecta.test");
        when(usuarioDAO.buscarPorEmail("borrado@mediconecta.test")).thenReturn(Optional.empty());
        assertFalse(decorador.ficha(7L).isDatosClinicosVisibles());
    }

    @Test
    void unPacienteSinHistoriaTieneLaFichaSinEntradas() {
        comoUsuario("ana.lopez@mediconecta.test", "PROFESIONAL", true);
        when(historias.buscarPorPaciente(7L)).thenReturn(null);

        FichaPaciente ficha = decorador.ficha(7L);

        assertTrue(ficha.isDatosClinicosVisibles());
        assertEquals(0, ficha.getTotalDeEntradas());
    }

    @Test
    void siNoEsUnPacienteNoHayFicha() {
        when(usuarioDAO.buscarPorId(20L)).thenReturn(tratante);

        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.ficha(20L));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.ficha(99L));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.ficha(null));
    }

    @Test
    void elDecoradorEstaHabilitadoYElServicioSoloLoPidenProfesionalesYAdministrativos() throws Exception {
        assertNotNull(PacienteEnmascarado.class.getAnnotation(Decorator.class));
        assertNotNull(PacienteEnmascarado.class.getAnnotation(Priority.class), "habilitado sin beans.xml");
        RolesAllowed roles = ServicioDePacientesBean.class.getMethod("ficha", Long.class).getAnnotation(RolesAllowed.class);
        assertArrayEquals(new String[]{"PROFESIONAL", "ADMINISTRATIVO"}, roles.value());
    }
}
