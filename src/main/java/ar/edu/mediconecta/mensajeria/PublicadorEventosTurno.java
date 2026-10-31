package ar.edu.mediconecta.mensajeria;

import ar.edu.mediconecta.dao.EventoPublicadoDAO;
import ar.edu.mediconecta.modelo.EventoPublicado;
import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.Topic;

import java.util.List;
import java.util.logging.Logger;

/**
 * Productor de {@code topico.turnos} (Publish-Subscribe Channel).
 *
 * <p>Transactional Client: el {@link JMSContext} inyectado usa la connection factory pooled/XA de WildFly,
 * así que el envío se enlista en la transacción JTA de quien llama (REQUIRED por defecto). El mensaje recién
 * sale del broker con el commit; si la operación hace rollback, el evento no se publica.
 *
 * <p>Las propiedades {@code tipoEvento} y {@code cobertura} viajan como cabeceras del mensaje para que los
 * suscriptores puedan filtrar con selectores sin abrir el cuerpo (Selective Consumer).
 */
@Stateless
public class PublicadorEventosTurno {

    private static final Logger LOG = Logger.getLogger(PublicadorEventosTurno.class.getName());

    @Inject
    private JMSContext jms;

    @Resource(lookup = "java:/jms/topic/turnos")
    private Topic topicoTurnos;

    @Inject
    private EventoPublicadoDAO eventoPublicadoDAO;

    public void publicar(EventoTurno evento) {
        List<String> destinatarios = Suscripciones.DEL_TOPICO.stream()
                .filter(s -> Suscripciones.recibe(s, evento.tipo().name()))
                .toList();
        eventoPublicadoDAO.guardar(new EventoPublicado(evento.idEvento(), evento.tipo().name(), evento.turnoId(),
                evento.fechaHoraTurno(), evento.pacienteId(), evento.profesionalId(), destinatarios));
        jms.createProducer()
                .setProperty("tipoEvento", evento.tipo().name())
                .setProperty("cobertura", evento.cobertura() == null ? "NINGUNA" : evento.cobertura())
                .send(topicoTurnos, EventoTurnoJson.aJson(evento));
        LOG.info("Evento " + evento.tipo() + " del turno " + evento.turnoId()
                + " encolado en topico.turnos (idEvento=" + evento.idEvento() + ").");
    }
}
