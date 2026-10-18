package ar.edu.mediconecta.mensajeria;

import ar.edu.mediconecta.dao.EventoProcesadoDAO;
import ar.edu.mediconecta.dao.RegistroAuditoriaDAO;
import ar.edu.mediconecta.modelo.RegistroAuditoria;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import java.util.logging.Logger;

/**
 * Segundo suscriptor durable de {@code topico.turnos}: deja una traza de cada evento de turno.
 * Junto con {@link NotificacionesSubscriber} muestra el Publish-Subscribe Channel: el mismo
 * evento le llega a dos consumidores que hacen cosas distintas, sin que el productor los conozca.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/turnos"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = Suscripciones.AUDITORIA),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-auditoria"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
public class AuditoriaSubscriber implements MessageListener {

    private static final Logger LOG = Logger.getLogger(AuditoriaSubscriber.class.getName());

    @Inject
    private EventoProcesadoDAO eventosProcesados;

    @Inject
    private RegistroAuditoriaDAO registroAuditoriaDAO;

    @Override
    public void onMessage(Message mensaje) {
        EventoTurno evento = EventoTurnoJson.desdeMensaje(mensaje);
        try {
            eventosProcesados.registrar(Suscripciones.AUDITORIA, evento.idEvento());
        } catch (EventoDuplicadoException duplicado) {
            LOG.info("Evento duplicado descartado: " + evento.idEvento());
            return;
        }
        registroAuditoriaDAO.guardar(new RegistroAuditoria(evento.idEvento(), evento.tipo().name(),
                evento.turnoId(), evento.pacienteId(), evento.profesionalId(), evento.ocurridoEn()));
        LOG.info("[Auditoría] " + evento.tipo() + " turno=" + evento.turnoId()
                + " paciente=" + evento.pacienteId() + " profesional=" + evento.profesionalId());
    }
}
