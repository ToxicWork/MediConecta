package ar.edu.mediconecta.mensajeria;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.DeliveryMode;
import jakarta.jms.JMSContext;
import jakarta.jms.Queue;

import java.util.logging.Logger;

/**
 * Productor de {@code cola.autorizaciones} (Point-to-Point Channel): cada solicitud la procesa exactamente un
 * consumidor. Igual que el publicador del tópico, el envío se enlista en la transacción de la confirmación
 * (Transactional Client). Mensajes persistentes (Guaranteed Delivery) con vencimiento a las 24 horas
 * (Message Expiration): una autorización vieja ya no sirve y no debe acumularse en la cola.
 */
@Stateless
public class ProductorSolicitudesAutorizacion {

    static final long VENCIMIENTO_MS = 24L * 60 * 60 * 1000;

    private static final Logger LOG = Logger.getLogger(ProductorSolicitudesAutorizacion.class.getName());

    @Inject
    private JMSContext jms;

    @Resource(lookup = "java:/jms/queue/autorizaciones")
    private Queue colaAutorizaciones;

    public void enviar(MensajeAutorizacion mensaje) {
        jms.createProducer()
                .setDeliveryMode(DeliveryMode.PERSISTENT)
                .setTimeToLive(VENCIMIENTO_MS)
                .setProperty("idSolicitud", mensaje.idSolicitud())
                .setProperty("obraSocial", mensaje.obraSocial())
                .send(colaAutorizaciones, MensajeJson.aJson(mensaje));
        LOG.info("Solicitud de autorización " + mensaje.idSolicitud() + " del turno " + mensaje.turnoId()
                + " encolada en cola.autorizaciones (TTL 24 h).");
    }
}
