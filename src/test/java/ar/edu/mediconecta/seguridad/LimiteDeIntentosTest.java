package ar.edu.mediconecta.seguridad;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fuerza bruta sobre el login (Eje D): al quinto fallo seguido con un email, 15 minutos de 429. */
class LimiteDeIntentosTest {

    private static final Instant T0 = Instant.parse("2026-11-18T15:00:00Z");
    private static final Duration QUINCE = Duration.ofMinutes(15);
    private static final String EMAIL = "paciente@mediconecta.test";

    private final LimiteDeIntentos limite = new LimiteDeIntentos(5, QUINCE);

    private void fallar(String email, int veces, Instant cuando) {
        for (int i = 0; i < veces; i++) {
            limite.registrarFallo(email, cuando);
        }
    }

    @Test
    void conCuatroFallosTodaviaSePuedeIntentar() {
        fallar(EMAIL, 4, T0);

        assertTrue(limite.espera(EMAIL, T0).isEmpty());
    }

    @Test
    void alQuintoFalloSeguidoElEmailQuedaBloqueadoQuinceMinutos() {
        fallar(EMAIL, 4, T0);

        assertTrue(limite.registrarFallo(EMAIL, T0), "el quinto fallo bloquea");
        assertEquals(QUINCE, limite.espera(EMAIL, T0).orElseThrow());
        assertEquals(Duration.ofMinutes(5), limite.espera(EMAIL, T0.plus(Duration.ofMinutes(10))).orElseThrow());
        assertTrue(limite.espera(EMAIL, T0.plus(QUINCE)).isEmpty(), "a los 15 minutos se puede volver a intentar");
    }

    @Test
    void unIngresoCorrectoPoneElContadorEnCero() {
        fallar(EMAIL, 4, T0);
        limite.registrarIngreso(EMAIL);
        fallar(EMAIL, 4, T0);

        assertTrue(limite.espera(EMAIL, T0).isEmpty());
    }

    @Test
    void mientrasDuraElBloqueoLosFallosNoLoAlargan() {
        fallar(EMAIL, 5, T0);
        fallar(EMAIL, 3, T0.plus(Duration.ofMinutes(5)));

        assertEquals(Duration.ofMinutes(10), limite.espera(EMAIL, T0.plus(Duration.ofMinutes(5))).orElseThrow());
    }

    @Test
    void pasadoElBloqueoLaCuentaEmpiezaDeCero() {
        fallar(EMAIL, 5, T0);
        Instant despues = T0.plus(Duration.ofMinutes(16));
        fallar(EMAIL, 4, despues);

        assertTrue(limite.espera(EMAIL, despues).isEmpty());
        assertTrue(limite.registrarFallo(EMAIL, despues));
    }

    @Test
    void elEmailNoDistingueMayusculasNiEspacios() {
        fallar(" Paciente@MediConecta.TEST ", 5, T0);

        assertTrue(limite.espera(EMAIL, T0).isPresent());
    }

    @Test
    void cadaEmailTieneSuPropioContador() {
        fallar(EMAIL, 5, T0);

        assertTrue(limite.espera("otro@mediconecta.test", T0).isEmpty());
    }

    @Test
    void olvidarLevantaElBloqueo() {
        fallar(EMAIL, 5, T0);
        limite.olvidar(EMAIL);

        assertTrue(limite.espera(EMAIL, T0).isEmpty());
    }

    @Test
    void conElRegistroLlenoNoAnotaEmailsNuevosPeroSigueContandoLosQueYaTiene() {
        for (int i = 0; i < LimiteDeIntentos.MAXIMO_DE_EMAILS; i++) {
            limite.registrarFallo("ataque" + i + "@x.test", T0);
        }

        fallar("nuevo@x.test", 5, T0);
        fallar("ataque0@x.test", 4, T0);

        assertTrue(limite.espera("nuevo@x.test", T0).isEmpty(), "sin lugar, el email nuevo no se registra");
        assertTrue(limite.espera("ataque0@x.test", T0).isPresent());
        assertEquals(LimiteDeIntentos.MAXIMO_DE_EMAILS, limite.emailsRegistrados());
    }

    @Test
    void conElRegistroLlenoDescartaLosFallosViejos() {
        for (int i = 0; i < LimiteDeIntentos.MAXIMO_DE_EMAILS; i++) {
            limite.registrarFallo("viejo" + i + "@x.test", T0);
        }
        Instant mediaHoraDespues = T0.plus(Duration.ofMinutes(30));

        assertFalse(limite.registrarFallo(EMAIL, mediaHoraDespues));
        assertEquals(1, limite.emailsRegistrados());
    }
}
