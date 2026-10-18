package ar.edu.mediconecta.mensajeria;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/** Formato del cuerpo de los mensajes de {@code topico.turnos}: JSON vía JSON-B. */
final class EventoTurnoJson {

    private static final Jsonb JSONB = JsonbBuilder.create();

    private EventoTurnoJson() {
    }

    static String aJson(EventoTurno evento) {
        return JSONB.toJson(evento);
    }

    static EventoTurno desdeMensaje(Message mensaje) {
        try {
            return JSONB.fromJson(mensaje.getBody(String.class), EventoTurno.class);
        } catch (JMSException e) {
            throw new IllegalStateException("No se pudo leer el cuerpo del mensaje", e);
        }
    }
}
