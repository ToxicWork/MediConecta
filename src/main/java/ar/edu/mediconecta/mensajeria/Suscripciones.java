package ar.edu.mediconecta.mensajeria;

import java.util.List;
import java.util.Set;

/** Nombres de las suscripciones durables de {@code topico.turnos} y de los consumidores de cola. */
public final class Suscripciones {

    public static final String NOTIFICACIONES = "notificaciones";
    public static final String AUDITORIA = "auditoria";
    public static final String FACTURACION = "facturacion";

    /** Consumidor de {@code cola.autorizaciones} (para su registro de idempotencia). */
    public static final String AUTORIZACIONES = "autorizaciones";

    /** Selective Consumer: Facturación sólo recibe los eventos que le generan trabajo. */
    public static final String SELECTOR_FACTURACION = "tipoEvento IN ('TURNO_AUTORIZADO','TURNO_CANCELADO')";
    private static final Set<String> TIPOS_FACTURACION = Set.of("TURNO_AUTORIZADO", "TURNO_CANCELADO");

    /** Suscripciones del tópico, en el orden en que las muestra el monitor. */
    public static final List<String> DEL_TOPICO = List.of(NOTIFICACIONES, AUDITORIA, FACTURACION);

    private Suscripciones() {
    }

    /** Replica el selector del broker para que el monitor sepa qué suscripción debía recibir cada evento. */
    public static boolean recibe(String suscripcion, String tipoEvento) {
        return !FACTURACION.equals(suscripcion) || TIPOS_FACTURACION.contains(tipoEvento);
    }
}
