package ar.edu.mediconecta.integracion.resiliencia;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CircuitBreakerTest {

    /** Reloj que se adelanta a mano, para probar la espera del circuito sin dormir. */
    static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-01T10:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojManual reloj = new RelojManual();
    private final CircuitBreaker circuito = new CircuitBreaker("legado", 3, Duration.ofSeconds(30), reloj);
    private final AtomicInteger llamadas = new AtomicInteger();

    private String falla() {
        llamadas.incrementAndGet();
        throw new IntegracionNoDisponibleException("timeout", true);
    }

    private String exito() {
        llamadas.incrementAndGet();
        return "ok";
    }

    @Test
    void seAbreTrasTresFallasSeguidasYRechazaSinLlamar() {
        for (int i = 0; i < 3; i++) {
            assertThrows(IntegracionNoDisponibleException.class, () -> circuito.ejecutar(this::falla));
        }
        assertEquals(CircuitBreaker.Estado.ABIERTO, circuito.getEstado());
        assertThrows(CircuitoAbiertoException.class, () -> circuito.ejecutar(this::exito));
        assertEquals(3, llamadas.get(), "con el circuito abierto no se llama al sistema externo");
        assertEquals(1, circuito.instantanea().getLlamadasRechazadas());
    }

    @Test
    void pasadaLaEsperaUnaPruebaExitosaLoCierra() {
        for (int i = 0; i < 3; i++) {
            assertThrows(IntegracionNoDisponibleException.class, () -> circuito.ejecutar(this::falla));
        }
        reloj.avanzar(Duration.ofSeconds(31));
        assertEquals(CircuitBreaker.Estado.SEMIABIERTO, circuito.getEstado());
        assertEquals("ok", circuito.ejecutar(this::exito));
        assertEquals(CircuitBreaker.Estado.CERRADO, circuito.getEstado());
    }

    @Test
    void unaPruebaFallidaLoVuelveAAbrir() {
        for (int i = 0; i < 3; i++) {
            assertThrows(IntegracionNoDisponibleException.class, () -> circuito.ejecutar(this::falla));
        }
        reloj.avanzar(Duration.ofSeconds(31));
        assertThrows(IntegracionNoDisponibleException.class, () -> circuito.ejecutar(this::falla));
        assertEquals(CircuitBreaker.Estado.ABIERTO, circuito.getEstado());
    }

    @Test
    void unExitoReiniciaElContadorDeFallas() {
        assertThrows(IntegracionNoDisponibleException.class, () -> circuito.ejecutar(this::falla));
        assertThrows(IntegracionNoDisponibleException.class, () -> circuito.ejecutar(this::falla));
        circuito.ejecutar(this::exito);
        assertThrows(IntegracionNoDisponibleException.class, () -> circuito.ejecutar(this::falla));
        assertEquals(CircuitBreaker.Estado.CERRADO, circuito.getEstado());
    }

    @Test
    void unErrorQueNoEsDeDisponibilidadNoAbreElCircuito() {
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> circuito.ejecutar(() -> {
                throw new IllegalArgumentException("dato mal mapeado");
            }));
        }
        assertEquals(CircuitBreaker.Estado.CERRADO, circuito.getEstado());
    }
}
