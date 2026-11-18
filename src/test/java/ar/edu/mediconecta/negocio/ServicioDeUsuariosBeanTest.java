package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.DatosDeUsuarioInvalidosException;
import ar.edu.mediconecta.negocio.excepciones.EmailYaRegistradoException;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import ar.edu.mediconecta.negocio.factory.UsuarioFactory;
import ar.edu.mediconecta.seguridad.LimiteDeIntentos;
import jakarta.ejb.SessionContext;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Registro de pacientes, alta por un administrativo, cambio de contraseña y desbloqueo de cuentas. */
class ServicioDeUsuariosBeanTest {

    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final Pbkdf2PasswordHash hash = mock(Pbkdf2PasswordHash.class);
    private final SessionContext contexto = mock(SessionContext.class);
    private final LimiteDeIntentos limite = new LimiteDeIntentos();
    private ServicioDeUsuariosBean servicio;

    @BeforeEach
    void armar() {
        servicio = new ServicioDeUsuariosBean();
        Inyectar.en(servicio, "usuarioDAO", usuarioDAO);
        Inyectar.en(servicio, "limiteDeIntentos", limite);
        Inyectar.en(servicio, "usuarioFactory", new UsuarioFactory());
        Inyectar.en(servicio, "passwordHash", hash);
        Inyectar.en(servicio, "contexto", contexto);
        // El hash de prueba es reconocible: así se ve que nunca se guarda la contraseña en claro.
        when(hash.generate(any())).thenAnswer(i -> "pbkdf2:" + new String(i.<char[]>getArgument(0)));
        when(hash.verify(any(), anyString())).thenAnswer(i -> ("pbkdf2:" + new String(i.<char[]>getArgument(0))).equals(i.getArgument(1)));
        when(contexto.getCallerPrincipal()).thenReturn(() -> "carla.admin@mediconecta.test");
    }

    private static DatosRegistro paciente(String nombre, String email) {
        return new DatosRegistro(nombre, email, "OSDE", "210-123", null, null);
    }

    @Test
    void registrarUnPacienteGuardaLosDatosNormalizadosYLaContrasenaHasheada() {
        Usuario nuevo = servicio.registrarPaciente(paciente("  Ana Gómez ", " ana.gomez@mediconecta.test "), "secreta-2026");

        assertInstanceOf(Paciente.class, nuevo);
        assertEquals("Ana Gómez", nuevo.getNombreCompleto());
        assertEquals("ana.gomez@mediconecta.test", nuevo.getEmail());
        assertEquals("pbkdf2:secreta-2026", nuevo.getPassword());
        assertFalse(nuevo.isDebeCambiarPassword());
        verify(usuarioDAO).guardar(nuevo);
    }

    @Test
    void sinNombreConUnEmailInvalidoOConUnaContrasenaCortaNoSeRegistra() {
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.registrarPaciente(paciente(" ", "a@b.test"), "secreta-2026"));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.registrarPaciente(paciente("Ana", "sin-arroba"), "secreta-2026"));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.registrarPaciente(paciente("Ana", "a@b.test"), "corta"));
        verify(usuarioDAO, never()).guardar(any());
    }

    @Test
    void unEmailYaRegistradoNoSeVuelveARegistrar() {
        when(usuarioDAO.existeEmail("juan.perez@mediconecta.test")).thenReturn(true);

        assertThrows(EmailYaRegistradoException.class,
                () -> servicio.registrarPaciente(paciente("Juan", "juan.perez@mediconecta.test"), "secreta-2026"));
        verify(usuarioDAO, never()).guardar(any());
    }

    @Test
    void elAdministrativoDaDeAltaUnProfesionalConContrasenaTemporal() {
        Usuario nuevo = servicio.crearUsuario(Rol.PROFESIONAL,
                new DatosRegistro("Dr. Pablo Ruiz", "pablo.ruiz@mediconecta.test", null, null, "MN 4321", "Dermatología"), "Temporal-2026");

        Profesional profesional = assertInstanceOf(Profesional.class, nuevo);
        assertEquals("Dermatología", profesional.getEspecialidad());
        assertTrue(nuevo.isDebeCambiarPassword(), "la contraseña temporal se cambia en el primer ingreso");
    }

    @Test
    void unProfesionalSinMatriculaOEspecialidadOSinRolNoSeDaDeAlta() {
        DatosRegistro incompleto = new DatosRegistro("Dr. Pablo Ruiz", "pablo@mediconecta.test", null, null, "MN 4321", " ");

        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.crearUsuario(Rol.PROFESIONAL, incompleto, "Temporal-2026"));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.crearUsuario(null, incompleto, "Temporal-2026"));
        verify(usuarioDAO, never()).guardar(any());
    }

    @Test
    void cambiarLaContrasenaExigeLaActualYQueLaNuevaSeaDistinta() {
        Paciente juan = Fixtures.paciente(7L, "OSDE");
        juan.cambiarPassword("pbkdf2:actual-2026");
        when(contexto.getCallerPrincipal()).thenReturn(juan::getEmail);
        when(usuarioDAO.buscarPorEmail(juan.getEmail())).thenReturn(Optional.of(juan));

        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.cambiarMiPassword("otra", "nueva-2026", "nueva-2026"));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.cambiarMiPassword("actual-2026", "actual-2026", "actual-2026"));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.cambiarMiPassword("actual-2026", "nueva-2026", "otra-2026"));

        servicio.cambiarMiPassword("actual-2026", "nueva-2026", "nueva-2026");
        assertEquals("pbkdf2:nueva-2026", juan.getPassword());
    }

    /** El desbloqueo de un administrativo también levanta el límite de intentos (el 429 de la API). */
    @Test
    void desbloquearLevantaElBloqueoDeLaCuentaYElLimiteDeIntentos() {
        Paciente juan = Fixtures.paciente(7L, "OSDE");
        for (int i = 0; i < 5; i++) {
            juan.registrarIntentoFallido(LocalDateTime.now());
            limite.registrarFallo(juan.getEmail());
        }
        when(usuarioDAO.buscarPorId(7L)).thenReturn(juan);

        servicio.desbloquear(7L);

        assertFalse(juan.estaBloqueado(LocalDateTime.now()));
        assertTrue(limite.espera(juan.getEmail()).isEmpty());
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> servicio.desbloquear(99L));
    }

    @Test
    void lasConsultasDevuelvenLoQueTieneLaBase() {
        Profesional ana = Fixtures.profesional(20L, "Cardiología");
        when(usuarioDAO.listarProfesionales()).thenReturn(List.of(ana));
        when(usuarioDAO.listarTodos()).thenReturn(List.of(ana));
        when(usuarioDAO.buscarPorEmail("ana@test")).thenReturn(Optional.of(ana));

        assertEquals(List.of(ana), servicio.listarProfesionales());
        assertEquals(List.of(ana), servicio.listarUsuarios());
        assertSame(ana, servicio.buscarPorEmail("ana@test").orElseThrow());
    }
}
