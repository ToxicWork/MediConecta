package ar.edu.mediconecta.mensajeria;

import ar.edu.mediconecta.dao.EventoProcesadoDAO;
import ar.edu.mediconecta.notificaciones.ProcesadorNotificacion;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import java.util.logging.Logger;

/**
 * ServicioDeNotificaciones: suscriptor durable de {@code topico.turnos} (Event-Driven Consumer).
 * Si la aplicación está caída cuando se publica un evento, el broker se lo retiene y lo
 * entrega al volver (Durable Subscriber).
 *
 * <p>{@code onMessage} corre en una transacción del contenedor (REQUIRED): si lanza una
 * excepción de sistema, el mensaje vuelve a la cola y se reintenta; tras los intentos
 * configurados en el address-setting termina en la DLQ.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/turnos"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = Suscripciones.NOTIFICACIONES),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-notificaciones"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class NotificacionesSubscriber implements MessageListener {

    private static final Logger LOG = Logger.getLogger(NotificacionesSubscriber.class.getName());

    @Inject
    private EventoProcesadoDAO eventosProcesados;

    @Inject
    @Any
    private Instance<ProcesadorNotificacion> procesadores;

    @Override
    public void onMessage(Message mensaje) {
        EventoTurno evento = EventoTurnoJson.desdeMensaje(mensaje);
        try {
            eventosProcesados.registrar(Suscripciones.NOTIFICACIONES, evento.idEvento());
        } catch (EventoDuplicadoException duplicado) {
            LOG.info("Evento duplicado descartado: " + evento.idEvento());
            return;
        }
        procesadores.forEach(procesador -> procesador.procesar(evento));
        LOG.info("Notificaciones generadas para " + evento.tipo() + " del turno " + evento.turnoId() + ".");
    }
}
