package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Administrativo;
import ar.edu.mediconecta.negocio.factory.UsuarioFactory;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El administrador por defecto al desplegar (G6: su contraseña inicial sale de la configuración, nunca del código). */
class InicializadorDeAdministradorTest {

    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final Pbkdf2PasswordHash hash = mock(Pbkdf2PasswordHash.class);
    private final UserTransaction transaccion = mock(UserTransaction.class);
    private InicializadorDeAdministrador inicializador;

    @BeforeEach
    void armar() {
        inicializador = new InicializadorDeAdministrador();
        Inyectar.en(inicializador, "usuarioDAO", usuarioDAO);
        Inyectar.en(inicializador, "usuarioFactory", new UsuarioFactory());
        Inyectar.en(inicializador, "passwordHash", hash);
        Inyectar.en(inicializador, "transaccion", transaccion);
        when(hash.generate(any())).thenAnswer(i -> "pbkdf2:" + new String(i.<char[]>getArgument(0)));
    }

    @AfterEach
    void limpiar() {
        System.clearProperty(InicializadorDeAdministrador.PROPIEDAD_CONTRASENA_INICIAL);
    }

    @Test
    void conLaContrasenaConfiguradaSeCreaElAdministradorConContrasenaTemporal() throws Exception {
        System.setProperty(InicializadorDeAdministrador.PROPIEDAD_CONTRASENA_INICIAL, "inicial-de-prueba");

        inicializador.asegurarAdministrador();

        verify(usuarioDAO).guardar(argThat(u -> u instanceof Administrativo
                && u.getEmail().equals(InicializadorDeAdministrador.EMAIL)
                && u.getPassword().equals("pbkdf2:inicial-de-prueba") && u.isDebeCambiarPassword()));
        verify(transaccion).commit();
    }

    @Test
    void siYaExisteNoSeVuelveACrear() throws Exception {
        System.setProperty(InicializadorDeAdministrador.PROPIEDAD_CONTRASENA_INICIAL, "inicial-de-prueba");
        when(usuarioDAO.existeEmail(InicializadorDeAdministrador.EMAIL)).thenReturn(true);

        inicializador.asegurarAdministrador();

        verify(usuarioDAO, never()).guardar(any());
        verify(transaccion).commit();
    }

    /** Sin contraseña configurada no hay administrador por defecto: la app despliega igual. */
    @Test
    void sinContrasenaConfiguradaNoSeCreaYLaAppSigue() throws Exception {
        inicializador.asegurarAdministrador();

        verify(usuarioDAO, never()).guardar(any());
        verify(transaccion).commit();
    }

    /** Sin base al arrancar (Supabase pausado), se revierte y se reintenta al próximo despliegue. */
    @Test
    void siLaBaseNoRespondeSeRevierteSinFrenarElDespliegue() throws Exception {
        when(usuarioDAO.existeEmail(any())).thenThrow(new IllegalStateException("sin conexión"));
        when(transaccion.getStatus()).thenReturn(Status.STATUS_ACTIVE);

        inicializador.asegurarAdministrador();

        verify(transaccion).rollback();
        verify(transaccion, never()).commit();
        verify(usuarioDAO, never()).guardar(any());
    }
}
