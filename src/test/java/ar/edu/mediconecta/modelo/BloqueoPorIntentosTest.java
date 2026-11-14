package ar.edu.mediconecta.modelo;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SEC-W01: cinco intentos fallidos seguidos bloquean la cuenta 15 minutos. */
class BloqueoPorIntentosTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 16, 10, 0);

    private final Usuario usuario = new Administrativo("Carla Admin", "carla@mediconecta.test", "hash");

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            usuario.registrarIntentoFallido(AHORA);
        }
    }

    @Test
    void cuatroFallosNoBloquean() {
        fallar(4);
        assertEquals(4, usuario.getIntentosFallidos());
        assertFalse(usuario.estaBloqueado(AHORA));
    }

    @Test
    void elQuintoFalloBloqueaQuinceMinutos() {
        fallar(4);
        assertTrue(usuario.registrarIntentoFallido(AHORA), "el quinto fallo es el que bloquea");
        assertTrue(usuario.estaBloqueado(AHORA));
        assertTrue(usuario.estaBloqueado(AHORA.plusMinutes(14).plusSeconds(59)));
        assertFalse(usuario.estaBloqueado(AHORA.plusMinutes(15)), "a los 15 minutos se desbloquea solo");
    }

    @Test
    void losIntentosDuranteElBloqueoNoLoExtienden() {
        fallar(5);
        assertFalse(usuario.registrarIntentoFallido(AHORA.plusMinutes(10)));
        assertEquals(AHORA.plusMinutes(15), usuario.getBloqueadoHasta());
    }

    @Test
    void cuandoVenceElBloqueoLaCuentaEmpiezaDeCero() {
        fallar(5);
        LocalDateTime despues = AHORA.plusMinutes(16);
        assertFalse(usuario.registrarIntentoFallido(despues), "un fallo después del bloqueo no vuelve a bloquear");
        assertEquals(1, usuario.getIntentosFallidos());
        assertFalse(usuario.estaBloqueado(despues));
    }

    @Test
    void unIngresoCorrectoPoneElContadorEnCero() {
        fallar(4);
        usuario.registrarIngresoExitoso();
        assertEquals(0, usuario.getIntentosFallidos());
        fallar(4);
        assertFalse(usuario.estaBloqueado(AHORA), "los fallos tienen que ser seguidos");
    }

    @Test
    void elAdministrativoPuedeDesbloquearAntes() {
        fallar(5);
        usuario.desbloquear();
        assertFalse(usuario.estaBloqueado(AHORA));
        assertEquals(0, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
    }
}
