package ar.edu.mediconecta.mensajeria;

import jakarta.jms.Message;

/** Cuerpo de los mensajes de {@code topico.turnos}. */
final class EventoTurnoJson {

    private EventoTurnoJson() {
    }

    static String aJson(EventoTurno evento) {
        return MensajeJson.aJson(evento);
    }

    static EventoTurno desdeMensaje(Message mensaje) {
        return MensajeJson.leer(mensaje, EventoTurno.class);
    }
}
