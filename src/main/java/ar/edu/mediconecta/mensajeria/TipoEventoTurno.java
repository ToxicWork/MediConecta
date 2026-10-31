package ar.edu.mediconecta.mensajeria;

public enum TipoEventoTurno {
    TURNO_CONFIRMADO,
    TURNO_CANCELADO,
    /** La obra social autorizó la prestación (lo publica el consumidor de la cola de autorizaciones). */
    TURNO_AUTORIZADO,
    AUTORIZACION_RECHAZADA
}
