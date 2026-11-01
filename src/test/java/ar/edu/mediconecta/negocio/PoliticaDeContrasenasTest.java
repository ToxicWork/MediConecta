package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.Administrativo;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.DatosDeUsuarioInvalidosException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de contraseñas (registro, alta por un administrativo y "Mi perfil") y contraseña temporal. */
class PoliticaDeContrasenasTest {

    @Test
    void aceptaUnaContrasenaValida() {
        assertDoesNotThrow(() -> PoliticaDeContrasenas.validar("demo1234"));
    }

    @Test
    void rechazaVaciaCortaLargaYConEspaciosEnLosBordes() {
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> PoliticaDeContrasenas.validar(null));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> PoliticaDeContrasenas.validar("   "));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> PoliticaDeContrasenas.validar("corta1"));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> PoliticaDeContrasenas.validar("x".repeat(65)));
        assertThrows(DatosDeUsuarioInvalidosException.class, () -> PoliticaDeContrasenas.validar(" demo1234 "));
    }

    @Test
    void elCambioExigeRepeticionIgualYDistintaDeLaActual() {
        var distintas = assertThrows(DatosDeUsuarioInvalidosException.class,
                () -> PoliticaDeContrasenas.validarCambio("demo1234", "nueva.2026", "nueva.2027"));
        assertEquals("La contraseña nueva y su repetición no coinciden.", distintas.getMessage());
        assertThrows(DatosDeUsuarioInvalidosException.class,
                () -> PoliticaDeContrasenas.validarCambio("demo1234", "demo1234", "demo1234"));
        assertDoesNotThrow(() -> PoliticaDeContrasenas.validarCambio("demo1234", "nueva.2026", "nueva.2026"));
    }

    @Test
    void laContrasenaTemporalSeCumpleAlCambiarla() {
        Usuario usuario = new Administrativo("Administrador", "admin@mediconecta.test", "hash-temporal");
        assertFalse(usuario.isDebeCambiarPassword(), "un usuario nuevo no arrastra la marca");
        usuario.exigirCambioDePassword();
        assertTrue(usuario.isDebeCambiarPassword());
        usuario.cambiarPassword("hash-nuevo");
        assertFalse(usuario.isDebeCambiarPassword());
        assertEquals("hash-nuevo", usuario.getPassword());
    }
}
