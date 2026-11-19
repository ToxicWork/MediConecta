package ar.edu.mediconecta.it;

import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.annotation.Resource;
import jakarta.inject.Inject;
import jakarta.jms.JMSContext;
import jakarta.jms.Topic;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Consumidores de mensajes con el broker real (criterios de la Clase 15: "broker real, no mocks en memoria"). La
 * prueba publica en {@code topico.turnos} el mismo mensaje que publica la aplicación (JSON, con la propiedad
 * {@code tipoEvento}), y los suscriptores durables de mediconecta.war, ya desplegada, lo procesan de verdad: el de
 * notificaciones deja el aviso en la bandeja de la profesional y el de auditoría, el registro. Si el broker entrega el
 * mismo evento dos veces, el aviso no se duplica (Idempotent Receiver). Lo que crea lo borra al terminar.
 */
@ExtendWith(ArquillianExtension.class)
class ConsumidoresDeMensajesIT {

    private static final long ESPERA_MAXIMA_MS = 20_000;

    @Deployment
    static WebArchive despliegue() {
        return Despliegues.conModeloYRepositorios("consumidores-it", ConsumidoresDeMensajesIT.class);
    }

    @Inject
    private BaseDePruebas base;

    @Inject
    private JMSContext jms;

    @Resource(lookup = "java:/jms/topic/turnos")
    private Topic topicoTurnos;

    private String sufijo;
    private String idEvento;

    @AfterEach
    void borrarLoCreado() throws Exception {
        if (idEvento != null) {
            base.borrar(List.of("DELETE FROM notificacion WHERE id_evento = ?1",
                    "DELETE FROM registro_auditoria WHERE id_evento = ?1",
                    "DELETE FROM evento_procesado WHERE clave LIKE '%' || ?1"), idEvento);
        }
        if (sufijo != null) {
            base.borrar(List.of("DELETE FROM turno WHERE profesional_id IN (SELECT id FROM usuario WHERE email LIKE ?1)",
                    "DELETE FROM usuario WHERE email LIKE ?1"), "it.%." + sufijo + "@mediconecta.test");
        }
    }

    @Test
    void elSuscriptorDeNotificacionesProcesaElEventoRealUnaSolaVez() throws Exception {
        sufijo = BaseDePruebas.sufijo();
        Turno turno = base.enTransaccion(() -> {
            Profesional profesional = base.profesional(sufijo);
            Paciente paciente = base.paciente(sufijo, null);
            return base.turnoConfirmado(profesional, paciente, LocalDateTime.now().plusDays(91).withNano(0));
        });
        idEvento = UUID.randomUUID().toString();
        String mensaje;
        try (Jsonb json = JsonbBuilder.create()) { // adentro del contenedor: el JSON-B de WildFly, el mismo que usa la aplicación
            mensaje = json.toJson(evento(turno));
        }

        publicar(mensaje);
        long avisos = esperar("SELECT count(*) FROM notificacion WHERE id_evento = ?1 AND destinatario_id = "
                + turno.getProfesional().getId(), 1);
        assertEquals(1, avisos, "el aviso llega a la bandeja de la profesional");
        assertEquals(1, esperar("SELECT count(*) FROM registro_auditoria WHERE id_evento = ?1", 1), "y queda en la auditoría");

        publicar(mensaje); // el broker lo vuelve a entregar
        Thread.sleep(5_000);
        assertEquals(1, base.contar("SELECT count(*) FROM notificacion WHERE id_evento = ?1 AND destinatario_id = "
                + turno.getProfesional().getId(), idEvento), "el mismo evento dos veces no duplica el aviso");
        assertEquals(1, base.contar("SELECT count(*) FROM registro_auditoria WHERE id_evento = ?1", idEvento));
    }

    private EventoTurno evento(Turno turno) {
        return new EventoTurno(idEvento, TipoEventoTurno.TURNO_CONFIRMADO, LocalDateTime.now(), turno.getId(),
                turno.getFechaHora(), turno.getModalidad().name(), turno.getProfesional().getId(),
                turno.getProfesional().getNombreCompleto(), turno.getProfesional().getEspecialidad(),
                turno.getPaciente().getId(), turno.getPaciente().getNombreCompleto(), turno.getPaciente().getEmail(),
                turno.getObraSocial(), turno.getCobertura() == null ? null : turno.getCobertura().name(),
                turno.getImportePaciente(), turno.getImporteObraSocial(), null, null, null);
    }

    private void publicar(String mensaje) {
        jms.createProducer().setProperty("tipoEvento", TipoEventoTurno.TURNO_CONFIRMADO.name())
                .setProperty("cobertura", "NINGUNA").send(topicoTurnos, mensaje);
    }

    /** Espera a que el consumidor de la aplicación procese el mensaje: lo hace en su propio hilo y transacción. */
    private long esperar(String sql, long esperado) throws InterruptedException {
        long hasta = System.currentTimeMillis() + ESPERA_MAXIMA_MS;
        long actual = base.contar(sql, idEvento);
        while (actual < esperado && System.currentTimeMillis() < hasta) {
            Thread.sleep(500);
            actual = base.contar(sql, idEvento);
        }
        return actual;
    }
}
