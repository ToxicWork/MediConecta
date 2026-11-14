package ar.edu.mediconecta.seguridad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** SEC-D02: el log muestra el email enmascarado. */
class EnmascaradorTest {

    @Test
    void conservaLosDosPrimerosCaracteresYElDominio() {
        assertEquals("ju***@mediconecta.test", Enmascarador.email("juan.perez@mediconecta.test"));
        assertEquals("ca***@mediconecta.test", Enmascarador.email("carla.admin@mediconecta.test"));
    }

    @Test
    void unUsuarioCortoMuestraSoloUnCaracter() {
        assertEquals("a***@mediconecta.test", Enmascarador.email("admin@mediconecta.test".replace("admin", "ana")));
        assertEquals("a***@x.com", Enmascarador.email("a@x.com"));
    }

    @Test
    void nuncaDevuelveElDatoOriginal() {
        String email = "seguridad.12345@mediconecta.test";
        assertFalse(Enmascarador.email(email).contains("seguridad.12345"));
    }

    @Test
    void siNoEsUnEmailNoMuestraNada() {
        assertEquals("***", Enmascarador.email(null));
        assertEquals("***", Enmascarador.email("sin-arroba"));
        assertEquals("***", Enmascarador.email("@sin-usuario.com"));
    }
}
