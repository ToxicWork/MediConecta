package ar.edu.mediconecta.mensajeria;

import ar.edu.mediconecta.dao.EventoProcesadoDAO;
import ar.edu.mediconecta.negocio.ServicioDeFacturacion;
import ar.edu.mediconecta.negocio.excepciones.PasarelaNoDisponibleException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import jakarta.ejb.MessageDrivenContext;
import jakarta.inject.Inject;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

import java.util.logging.Logger;

/**
 * ServicioDeFacturacion: tercer suscriptor durable de {@code topico.turnos}, con selector (Selective
 * Consumer). El broker sólo le entrega TURNO_AUTORIZADO y TURNO_CANCELADO; el resto de los eventos ni
 * siquiera llega a su cola de suscripción. Se sumó sin tocar el productor: es la ventaja del tópico.
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/topic/turnos"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Topic"),
        @ActivationConfigProperty(propertyName = "subscriptionDurability", propertyValue = "Durable"),
        @ActivationConfigProperty(propertyName = "subscriptionName", propertyValue = Suscripciones.FACTURACION),
        @ActivationConfigProperty(propertyName = "clientId", propertyValue = "mediconecta-facturacion"),
        @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = Suscripciones.SELECTOR_FACTURACION),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "1")
})
@RunAs("SISTEMA")
@PermitAll
@DeclareRoles("SISTEMA")
public class FacturacionSubscriber implements MessageListener {

    private static final Logger LOG = Logger.getLogger(FacturacionSubscriber.class.getName());

    @Resource
    private MessageDrivenContext contexto;

    @Inject
    private EventoProcesadoDAO eventosProcesados;

    @Inject
    private ServicioDeFacturacion facturacion;

    @Override
    public void onMessage(Message mensaje) {
        EventoTurno evento = EventoTurnoJson.desdeMensaje(mensaje);
        try {
            eventosProcesados.registrar(Suscripciones.FACTURACION, evento.idEvento());
        } catch (EventoDuplicadoException duplicado) {
            LOG.info("Evento duplicado descartado por facturación: " + evento.idEvento());
            return;
        }
        try {
            facturacion.procesarEvento(evento);
        } catch (PasarelaNoDisponibleException caida) {
            // El reintegro no se pudo hacer: se devuelve el mensaje (3 reintentos y luego DLQ para conciliar).
            LOG.warning("[Facturación] Pasarela no disponible para el reintegro del turno " + evento.turnoId()
                    + " (intento " + MensajeJson.intento(mensaje) + "): se reintentará.");
            contexto.setRollbackOnly();
        }
    }
}
