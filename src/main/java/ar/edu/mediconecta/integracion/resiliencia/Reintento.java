package ar.edu.mediconecta.integracion.resiliencia;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Retry: repite una llamada idempotente ante una falla técnica transitoria. Envuelve al Circuit Breaker
 * (mismo orden que MicroProfile Fault Tolerance: Fallback → Retry → CircuitBreaker → Timeout), de modo
 * que cada intento cuenta para el circuito; si el circuito ya está abierto no se reintenta.
 */
public final class Reintento {

    private Reintento() {
    }

    public static <T> T ejecutar(int intentos, Duration espera, Supplier<T> llamada) {
        IntegracionNoDisponibleException ultima = null;
        for (int intento = 1; intento <= intentos; intento++) {
            try {
                return llamada.get();
            } catch (IntegracionNoDisponibleException falla) {
                ultima = falla;
                if (!falla.isReintentable() || intento == intentos) {
                    throw falla;
                }
                dormir(espera);
            }
        }
        throw ultima;
    }

    private static void dormir(Duration espera) {
        try {
            Thread.sleep(espera.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
