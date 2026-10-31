package ar.edu.mediconecta.mensajeria;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;

/** Formato del cuerpo de los mensajes JMS de MediConecta: JSON vía JSON-B (sin acoplar a clases Java serializadas). */
final class MensajeJson {

    private static final Jsonb JSONB = JsonbBuilder.create();

    private MensajeJson() {
    }

    static String aJson(Object mensaje) {
        return JSONB.toJson(mensaje);
    }

    static <T> T leer(Message mensaje, Class<T> tipo) {
        try {
            return JSONB.fromJson(mensaje.getBody(String.class), tipo);
        } catch (JMSException e) {
            throw new IllegalStateException("No se pudo leer el cuerpo del mensaje", e);
        }
    }

    static int intento(Message mensaje) {
        try {
            return mensaje.getIntProperty("JMSXDeliveryCount");
        } catch (JMSException | NumberFormatException e) {
            return 1;
        }
    }
}
