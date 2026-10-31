package ar.edu.mediconecta.integracion.resiliencia;

import java.time.Instant;

/** El circuito está abierto: ni siquiera se intenta la llamada (falla rápida). No se reintenta. */
public class CircuitoAbiertoException extends IntegracionNoDisponibleException {

    public CircuitoAbiertoException(String circuito, Instant abiertoHasta) {
        super("Circuito abierto para " + circuito + (abiertoHasta == null ? "" : " hasta " + abiertoHasta), false);
    }
}
