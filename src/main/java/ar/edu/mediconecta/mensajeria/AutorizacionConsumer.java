package ar.edu.mediconecta.mensajeria;

import ar.edu.mediconecta.dao.EventoProcesadoDAO;
import ar.edu.mediconecta.dao.SolicitudAutorizacionDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.SolicitudAutorizacion;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.ServicioDeObrasSociales;
import ar.edu.mediconecta.negocio.excepciones.ObraSocialNoDisponibleException;
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
 * Consumidor de {@code cola.autorizaciones} (Event-Driven Consumer sobre un Point-to-Point Channel).
 *
 * <ul>
 *   <li>Competing Consumers: {@code maxSession=2} abre dos sesiones sobre la misma cola; el broker reparte los
 *       mensajes entre ellas y cada uno lo procesa una sola. Absorbe los picos sin duplicar trabajo.</li>
 *   <li>Si el legado no responde, no se confirma el mensaje ({@code setRollbackOnly}): el broker lo reentrega
 *       con espera creciente (10 s, 20 s, 40 s, 60 s...) y a los 8 intentos lo manda a la DLQ.</li>
 *   <li>Idempotent Receiver: {@code evento_procesado} con clave (autorizaciones, idSolicitud).</li>
 *   <li>La resolución actualiza el turno y publica TURNO_AUTORIZADO o AUTORIZACION_RECHAZADA en la misma
 *       transacción en que se consume el mensaje: o pasan las tres cosas, o ninguna.</li>
 *   <li>Corre con la identidad técnica SISTEMA ({@code @RunAs}): un MDB no tiene usuario que lo invoque.</li>
 * </ul>
 */
@MessageDriven(activationConfig = {
        @ActivationConfigProperty(propertyName = "destinationLookup", propertyValue = "java:/jms/queue/autorizaciones"),
        @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "jakarta.jms.Queue"),
        @ActivationConfigProperty(propertyName = "maxSession", propertyValue = "2")
})
@RunAs("SISTEMA")
@PermitAll
@DeclareRoles("SISTEMA")
public class AutorizacionConsumer implements MessageListener {

    private static final Logger LOG = Logger.getLogger(AutorizacionConsumer.class.getName());

    @Resource
    private MessageDrivenContext contexto;

    @Inject
    private EventoProcesadoDAO eventosProcesados;

    @Inject
    private SolicitudAutorizacionDAO solicitudes;

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private ServicioDeObrasSociales obrasSociales;

    @Inject
    private PublicadorEventosTurno publicador;

    @Override
    public void onMessage(Message mensaje) {
        MensajeAutorizacion pedido = MensajeJson.leer(mensaje, MensajeAutorizacion.class);
        int intento = MensajeJson.intento(mensaje);
        String consumidor = Thread.currentThread().getName() + " · instancia "
                + Integer.toHexString(System.identityHashCode(this));
        try {
            eventosProcesados.registrar(Suscripciones.AUTORIZACIONES, pedido.idSolicitud());
        } catch (EventoDuplicadoException duplicado) {
            LOG.info("Solicitud de autorización duplicada descartada: " + pedido.idSolicitud());
            return;
        }
        SolicitudAutorizacion solicitud = solicitudes.buscar(pedido.idSolicitud());
        Turno turno = turnoDAO.buscarPorId(pedido.turnoId());
        if (solicitud == null || turno == null || turno.getEstado() != EstadoTurno.CONFIRMADO) {
            if (solicitud != null) {
                solicitud.anular("El turno se canceló antes de que respondiera la obra social.", intento, consumidor);
            }
            LOG.info("Solicitud " + pedido.idSolicitud() + " anulada: el turno ya no está confirmado.");
            return;
        }

        ResultadoAutorizacion resultado;
        try {
            resultado = obrasSociales.autorizar(pedido);
        } catch (ObraSocialNoDisponibleException caida) {
            LOG.warning("[cola.autorizaciones] Intento " + intento + " de la solicitud " + pedido.idSolicitud()
                    + ": " + caida.getMessage() + ". Se devuelve el mensaje al broker para reintentar más tarde.");
            contexto.setRollbackOnly();
            return;
        }

        solicitud.resolver(resultado.autorizada(), resultado.codigoAutorizacion(), resultado.motivo(), intento, consumidor);
        turno.registrarAutorizacion(resultado.autorizada(), resultado.codigoAutorizacion(), resultado.motivo());
        publicador.publicar(EventoTurno.builder()
                .tipo(resultado.autorizada() ? TipoEventoTurno.TURNO_AUTORIZADO : TipoEventoTurno.AUTORIZACION_RECHAZADA)
                .turno(turno)
                .build());
        LOG.info("[cola.autorizaciones] Solicitud " + pedido.idSolicitud() + " resuelta en el intento " + intento
                + " por " + consumidor + ": " + (resultado.autorizada() ? "AUTORIZADA " + resultado.codigoAutorizacion()
                : "RECHAZADA (" + resultado.motivo() + ")"));
    }
}
