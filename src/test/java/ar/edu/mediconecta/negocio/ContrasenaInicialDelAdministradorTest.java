package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.negocio.excepciones.DatosDeUsuarioInvalidosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G6: la contraseña inicial del administrador por defecto sale de la configuración del servidor, nunca del código.
 */
class ContrasenaInicialDelAdministradorTest {

    @Test
    void laPropiedadDeSistemaTienePrioridadSobreLaVariableDeEntorno() {
        assertEquals("de-la-propiedad-1", InicializadorDeAdministrador.contrasenaInicial("de-la-propiedad-1", "de-la-variable-1"));
    }

    @Test
    void sinLaPropiedadSeUsaLaVariableDeEntorno() {
        assertEquals("de-la-variable-1", InicializadorDeAdministrador.contrasenaInicial(null, "de-la-variable-1"));
        assertEquals("de-la-variable-1", InicializadorDeAdministrador.contrasenaInicial("  ", "de-la-variable-1"));
    }

    @Test
    void sinNingunaNoHayContrasenaYElMotivoDiceQueConfigurar() {
        IllegalStateException falta = assertThrows(IllegalStateException.class,
                () -> InicializadorDeAdministrador.contrasenaInicial(null, " "));
        assertTrue(falta.getMessage().contains(InicializadorDeAdministrador.VARIABLE_CONTRASENA_INICIAL), falta.getMessage());
        assertTrue(falta.getMessage().contains(InicializadorDeAdministrador.PROPIEDAD_CONTRASENA_INICIAL), falta.getMessage());
    }

    @Test
    void unaContrasenaQueNoCumpleLaPoliticaNoSeUsa() {
        DatosDeUsuarioInvalidosException corta = assertThrows(DatosDeUsuarioInvalidosException.class,
                () -> InicializadorDeAdministrador.contrasenaInicial("corta", null));
        assertTrue(corta.getMessage().contains(String.valueOf(PoliticaDeContrasenas.LONGITUD_MINIMA)), corta.getMessage());
        assertThrows(DatosDeUsuarioInvalidosException.class,
                () -> InicializadorDeAdministrador.contrasenaInicial(" con-espacios-1 ", null));
    }
}
