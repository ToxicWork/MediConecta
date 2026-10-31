package ar.edu.mediconecta.integracion.resiliencia;

/**
 * Falla técnica al hablar con un sistema externo (sin respuesta, timeout, HTTP 5xx, SOAP Fault de
 * infraestructura). Es la única que cuenta para el Circuit Breaker: un "no tiene cobertura" o un
 * "pago rechazado" son respuestas válidas, no fallas.
 */
public class IntegracionNoDisponibleException extends RuntimeException {

    private final boolean reintentable;

    public IntegracionNoDisponibleException(String mensaje, boolean reintentable) {
        super(mensaje);
        this.reintentable = reintentable;
    }

    public IntegracionNoDisponibleException(String mensaje, boolean reintentable, Throwable causa) {
        super(mensaje, causa);
        this.reintentable = reintentable;
    }

    public boolean isReintentable() {
        return reintentable;
    }
}
