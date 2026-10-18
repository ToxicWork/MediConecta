package ar.edu.mediconecta.notificaciones;

import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.modelo.CanalNotificacion;
import ar.edu.mediconecta.modelo.Notificacion;
import jakarta.enterprise.context.Dependent;

/** Aviso dentro de la aplicación para el profesional: queda en su bandeja de notificaciones. */
@Dependent
public class ProcesadorAvisoProfesional extends ProcesadorNotificacion {

    @Override
    protected Notificacion crearNotificacion(EventoTurno evento) {
        String fecha = FORMATO_FECHA.format(evento.fechaHoraTurno());
        String destino = "Bandeja de " + evento.profesionalNombre();
        return switch (evento.tipo()) {
            case TURNO_CONFIRMADO -> new Notificacion(evento.profesionalId(), destino, CanalNotificacion.IN_APP,
                    "Nuevo turno confirmado",
                    evento.pacienteNombre() + " confirmó un turno para el " + fecha + ".",
                    evento.idEvento());
            case TURNO_CANCELADO -> new Notificacion(evento.profesionalId(), destino, CanalNotificacion.IN_APP,
                    "Turno cancelado",
                    evento.pacienteNombre() + " canceló su turno del " + fecha + ". El horario volvió a quedar disponible.",
                    evento.idEvento());
        };
    }

    @Override
    protected void enviar(Notificacion notificacion) {
        // El canal in-app no tiene envío externo: guardarla en la bandeja ya es la entrega.
    }
}
