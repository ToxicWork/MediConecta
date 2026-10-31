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
        String modalidad = "TELEMEDICINA".equals(evento.modalidad()) ? " (telemedicina)" : "";
        return switch (evento.tipo()) {
            case TURNO_CONFIRMADO -> aviso(evento, "Nuevo turno confirmado",
                    evento.pacienteNombre() + " confirmó un turno para el " + fecha + modalidad + ".");
            case TURNO_CANCELADO -> aviso(evento, "Turno cancelado",
                    evento.pacienteNombre() + " canceló su turno del " + fecha + ". El horario volvió a quedar disponible.");
            case TURNO_AUTORIZADO -> aviso(evento, "Prestación autorizada",
                    evento.obraSocial() + " autorizó el turno de " + evento.pacienteNombre() + " del " + fecha
                            + " (código " + evento.codigoAutorizacion() + "). Ya se generó el reclamo de facturación.");
            case AUTORIZACION_RECHAZADA -> aviso(evento, "Autorización rechazada",
                    evento.obraSocial() + " no autorizó el turno de " + evento.pacienteNombre() + " del " + fecha + ": "
                            + evento.motivoAutorizacion());
        };
    }

    private static Notificacion aviso(EventoTurno evento, String asunto, String cuerpo) {
        return new Notificacion(evento.profesionalId(), "Bandeja de " + evento.profesionalNombre(),
                CanalNotificacion.IN_APP, asunto, cuerpo, evento.idEvento());
    }

    @Override
    protected void enviar(Notificacion notificacion) {
        // El canal in-app no tiene envío externo: guardarla en la bandeja ya es la entrega.
    }
}
