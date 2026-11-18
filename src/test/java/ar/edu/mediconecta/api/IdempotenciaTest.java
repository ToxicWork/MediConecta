package ar.edu.mediconecta.api;

import ar.edu.mediconecta.negocio.excepciones.ParametroInvalidoException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Formato de la cabecera Idempotency-Key. */
class IdempotenciaTest {

    @Test
    void sinCabeceraNoHayClave() {
        assertNull(Idempotencia.validar(null));
    }

    @Test
    void unUuidOUnTextoCortoSinEspaciosSirven() {
        assertEquals("3f2c1d9e-8b7a-4c21-9e0f-1a2b3c4d5e6f", Idempotencia.validar("3f2c1d9e-8b7a-4c21-9e0f-1a2b3c4d5e6f"));
        assertEquals("cancelar.turno:42", Idempotencia.validar("  cancelar.turno:42 "));
    }

    @Test
    void conEspaciosVaciaODemasiadoLargaEs400() {
        assertThrows(ParametroInvalidoException.class, () -> Idempotencia.validar("dos palabras"));
        assertThrows(ParametroInvalidoException.class, () -> Idempotencia.validar("   "));
        assertThrows(ParametroInvalidoException.class, () -> Idempotencia.validar("x".repeat(101)));
        assertThrows(ParametroInvalidoException.class, () -> Idempotencia.validar("<script>"));
    }
}
