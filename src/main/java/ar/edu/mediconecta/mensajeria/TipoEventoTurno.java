package ar.edu.mediconecta.mensajeria;

public enum TipoEventoTurno {
    TURNO_CONFIRMADO,
    TURNO_CANCELADO,
    /** La obra social autorizó la prestación (lo publica el consumidor de la cola de autorizaciones). */
    TURNO_AUTORIZADO,
    AUTORIZACION_RECHAZADA,
    /** El profesional abrió la sala de la videoconsulta: se le avisa al paciente (D-31). Facturación no lo recibe. */
    SALA_ABIERTA
}
