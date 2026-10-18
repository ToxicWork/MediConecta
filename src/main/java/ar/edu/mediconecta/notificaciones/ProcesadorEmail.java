package ar.edu.mediconecta.notificaciones;

import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.modelo.CanalNotificacion;
import ar.edu.mediconecta.modelo.Notificacion;
import jakarta.enterprise.context.Dependent;

import java.util.logging.Logger;

/**
 * Email al paciente. El envío SMTP está simulado (se loguea): lo que se demuestra en esta
 * entrega es la mensajería asincrónica, no el proveedor de correo.
 */
@Dependent
public class ProcesadorEmail extends ProcesadorNotificacion {

    private static final Logger LOG = Logger.getLogger(ProcesadorEmail.class.getName());

    @Override
    protected Notificacion crearNotificacion(EventoTurno evento) {
        String fecha = FORMATO_FECHA.format(evento.fechaHoraTurno());
        String profesional = evento.especialidad() == null || evento.especialidad().isBlank()
                ? evento.profesionalNombre()
                : evento.profesionalNombre() + " (" + evento.especialidad() + ")";
        return switch (evento.tipo()) {
            case TURNO_CONFIRMADO -> new Notificacion(evento.pacienteId(), evento.pacienteEmail(), CanalNotificacion.EMAIL,
                    "Tu turno quedó confirmado",
                    "Hola " + evento.pacienteNombre() + ", confirmamos tu turno con " + profesional
                            + " el " + fecha + ".",
                    evento.idEvento());
            case TURNO_CANCELADO -> new Notificacion(evento.pacienteId(), evento.pacienteEmail(), CanalNotificacion.EMAIL,
                    "Cancelaste tu turno",
                    "Hola " + evento.pacienteNombre() + ", registramos la cancelación de tu turno con " + profesional
                            + " del " + fecha + ".",
                    evento.idEvento());
        };
    }

    @Override
    protected void enviar(Notificacion notificacion) {
        LOG.info("[SMTP simulado] Para: " + notificacion.getDestino() + " | Asunto: " + notificacion.getAsunto());
    }
}
