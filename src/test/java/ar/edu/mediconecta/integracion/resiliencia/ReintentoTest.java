package ar.edu.mediconecta.integracion.resiliencia;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReintentoTest {

    @Test
    void reintentaUnaFallaTransitoriaYDevuelveElResultado() {
        AtomicInteger intentos = new AtomicInteger();
        String resultado = Reintento.ejecutar(2, Duration.ZERO, () -> {
            if (intentos.incrementAndGet() == 1) {
                throw new IntegracionNoDisponibleException("timeout", true);
            }
            return "ok";
        });
        assertEquals("ok", resultado);
        assertEquals(2, intentos.get());
    }

    @Test
    void noReintentaConElCircuitoAbiertoNiLasFallasNoReintentables() {
        AtomicInteger intentos = new AtomicInteger();
        assertThrows(CircuitoAbiertoException.class, () -> Reintento.ejecutar(3, Duration.ZERO, () -> {
            intentos.incrementAndGet();
            throw new CircuitoAbiertoException("legado", null);
        }));
        assertEquals(1, intentos.get());
    }

    @Test
    void agotaLosIntentosYPropagaLaUltimaFalla() {
        AtomicInteger intentos = new AtomicInteger();
        assertThrows(IntegracionNoDisponibleException.class, () -> Reintento.ejecutar(2, Duration.ZERO, () -> {
            intentos.incrementAndGet();
            throw new IntegracionNoDisponibleException("caído", true);
        }));
        assertEquals(2, intentos.get());
    }
}
