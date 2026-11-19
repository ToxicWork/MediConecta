package ar.edu.mediconecta.modelo;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-06 y D-41: un reintegro que la pasarela no pudo hacer no se pierde, se reintenta con espera creciente. */
class ReintegroPendienteTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 19, 20, 0);

    private ReintegroPendiente pendiente() {
        return ReintegroPendiente.paraReintentar("pay_1", "turno-10", 10L, new BigDecimal("32000"), "Reversión",
                OrigenReintegro.COMPENSACION, "La pasarela no respondió", AHORA);
    }

    @Test
    void sinRespuestaQuedaPendienteYSeReintentaEnUnMinuto() {
        ReintegroPendiente r = pendiente();

        assertEquals(EstadoReintegro.PENDIENTE, r.getEstado());
        assertEquals(1, r.getIntentos(), "cuenta el intento que acaba de fallar");
        assertEquals(AHORA.plusMinutes(1), r.getProximoIntento());
        assertTrue(r.estaAbierto());
        assertEquals("pay_1", r.getIdPago());
        assertEquals("turno-10", r.getReferencia());
        assertEquals(10L, r.getTurnoId());
        assertEquals(new BigDecimal("32000"), r.getImporte());
        assertEquals("Reversión", r.getMotivo());
        assertEquals(OrigenReintegro.COMPENSACION, r.getOrigen());
        assertEquals("La pasarela no respondió", r.getUltimoError());
        assertEquals(AHORA, r.getCreadoEn());
    }

    @Test
    void cadaFallaEsperaElDobleHastaUnaHora() {
        assertEquals(1, ReintegroPendiente.esperaEnMinutos(1));
        assertEquals(2, ReintegroPendiente.esperaEnMinutos(2));
        assertEquals(4, ReintegroPendiente.esperaEnMinutos(3));
        assertEquals(32, ReintegroPendiente.esperaEnMinutos(6));
        assertEquals(60, ReintegroPendiente.esperaEnMinutos(7));
        assertEquals(60, ReintegroPendiente.esperaEnMinutos(40));

        ReintegroPendiente r = pendiente();
        r.registrarFalla("503", AHORA.plusMinutes(1));
        assertEquals(2, r.getIntentos());
        assertEquals(AHORA.plusMinutes(3), r.getProximoIntento());
        assertEquals("503", r.getUltimoError());
        assertEquals(EstadoReintegro.PENDIENTE, r.getEstado());
    }

    @Test
    void despuesDelMaximoDeIntentosPasaAConciliacionManual() {
        ReintegroPendiente r = pendiente();
        for (int i = 2; i < ReintegroPendiente.MAXIMO_DE_INTENTOS; i++) {
            r.registrarFalla("503", AHORA);
            assertEquals(EstadoReintegro.PENDIENTE, r.getEstado());
        }

        r.registrarFalla("503", AHORA);

        assertEquals(ReintegroPendiente.MAXIMO_DE_INTENTOS, r.getIntentos());
        assertEquals(EstadoReintegro.CONCILIACION_MANUAL, r.getEstado());
        assertNull(r.getProximoIntento(), "ya no lo reintenta el temporizador");
        assertTrue(r.getUltimoError().contains("10 intentos"));
        assertTrue(r.estaAbierto(), "lo sigue viendo el administrativo");
    }

    @Test
    void siLaPasarelaNoConoceElPagoQuedaParaConciliarSinReintentos() {
        ReintegroPendiente r = ReintegroPendiente.paraConciliar("pay_2", "turno-11", 11L, null, "Cancelación",
                OrigenReintegro.CANCELACION, "La pasarela no conoce el pago", AHORA);

        assertEquals(EstadoReintegro.CONCILIACION_MANUAL, r.getEstado());
        assertNull(r.getProximoIntento());
        assertTrue(r.estaAbierto());
    }

    @Test
    void hechoOConciliadoQuedaCerradoYNoSeTocaMas() {
        ReintegroPendiente hecho = pendiente();
        hecho.marcarReintegrado(AHORA.plusMinutes(2));
        assertEquals(EstadoReintegro.REINTEGRADO, hecho.getEstado());
        assertEquals(AHORA.plusMinutes(2), hecho.getResueltoEn());
        assertEquals("pasarela", hecho.getResueltoPor());
        assertFalse(hecho.estaAbierto());
        assertNull(hecho.getProximoIntento());
        assertThrows(ErrorDeNegocioException.class, () -> hecho.registrarFalla("503", AHORA));
        assertThrows(ErrorDeNegocioException.class, () -> hecho.marcarConciliado("carla", AHORA));

        ReintegroPendiente conciliado = pendiente();
        conciliado.pasarAConciliacionManual("No conoce el pago", AHORA);
        conciliado.marcarConciliado("carla.admin@mediconecta.test", AHORA.plusHours(1));
        assertEquals(EstadoReintegro.CONCILIADO, conciliado.getEstado());
        assertEquals("carla.admin@mediconecta.test", conciliado.getResueltoPor());
        assertFalse(conciliado.estaAbierto());
        assertThrows(ErrorDeNegocioException.class, () -> conciliado.marcarReintegrado(AHORA));
        assertThrows(ErrorDeNegocioException.class, () -> conciliado.pasarAConciliacionManual("x", AHORA));
    }

    @Test
    void sinIdDePagoNoHayReintegroYElErrorLargoSeRecorta() {
        assertThrows(IllegalArgumentException.class, () -> ReintegroPendiente.paraReintentar(" ", null, null, null, "m",
                OrigenReintegro.CANCELACION, null, AHORA));
        assertThrows(IllegalArgumentException.class, () -> ReintegroPendiente.paraConciliar(null, null, null, null, "m",
                OrigenReintegro.CANCELACION, null, AHORA));

        ReintegroPendiente r = pendiente();
        r.registrarFalla("x".repeat(500), AHORA);
        assertEquals(300, r.getUltimoError().length());
        r.registrarFalla("x".repeat(300), AHORA);
        assertEquals(300, r.getUltimoError().length());
        assertTrue(r.getUltimoError().endsWith("x"));
    }

    @Test
    void lasEtiquetasDicenLoQueVeElAdministrativo() {
        assertEquals("Pendiente: se reintenta solo", EstadoReintegro.PENDIENTE.getEtiqueta());
        assertEquals("Conciliación manual", EstadoReintegro.CONCILIACION_MANUAL.getEtiqueta());
        assertEquals("Confirmación revertida", OrigenReintegro.COMPENSACION.getEtiqueta());
        assertEquals("Turno cancelado", OrigenReintegro.CANCELACION.getEtiqueta());
    }
}
