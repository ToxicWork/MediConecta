package ar.edu.mediconecta.notificaciones;

import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.modelo.CanalNotificacion;
import ar.edu.mediconecta.modelo.Notificacion;
import jakarta.enterprise.context.Dependent;

import java.util.logging.Logger;

/**
 * Email al paciente. El envío SMTP está simulado (se loguea): lo que se demuestra es la mensajería
 * asincrónica, no el proveedor de correo.
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
        String modalidad = "TELEMEDICINA".equals(evento.modalidad()) ? "teleconsulta" : "turno presencial";
        return switch (evento.tipo()) {
            case TURNO_CONFIRMADO -> email(evento, "Tu turno quedó confirmado",
                    "Hola " + evento.pacienteNombre() + ", confirmamos tu " + modalidad + " con " + profesional
                            + " el " + fecha + "."
                            + (evento.tieneImporteAPagar() ? " Abonaste $ " + evento.importePaciente().toPlainString() + "." : "")
                            + ("PENDIENTE".equals(evento.cobertura())
                            ? " Tu obra social no respondió a tiempo: te avisamos cuando confirme la cobertura." : ""));
            case TURNO_CANCELADO -> email(evento, "Cancelaste tu turno",
                    "Hola " + evento.pacienteNombre() + ", registramos la cancelación de tu turno con " + profesional
                            + " del " + fecha + "."
                            + (evento.tieneImporteAPagar() ? " El reintegro de $ " + evento.importePaciente().toPlainString()
                            + " se acredita en tu tarjeta." : ""));
            case TURNO_AUTORIZADO -> email(evento, "Tu obra social autorizó el turno",
                    "Hola " + evento.pacienteNombre() + ", " + evento.obraSocial() + " autorizó la prestación de tu turno con "
                            + profesional + " del " + fecha + " (código " + evento.codigoAutorizacion() + ").");
            case AUTORIZACION_RECHAZADA -> email(evento, "Tu obra social no autorizó el turno",
                    "Hola " + evento.pacienteNombre() + ", " + evento.obraSocial() + " no autorizó la prestación de tu turno del "
                            + fecha + ": " + evento.motivoAutorizacion() + " El turno sigue confirmado; la diferencia se "
                            + "abona en el consultorio.");
        };
    }

    private static Notificacion email(EventoTurno evento, String asunto, String cuerpo) {
        return new Notificacion(evento.pacienteId(), evento.pacienteEmail(), CanalNotificacion.EMAIL, asunto, cuerpo,
                evento.idEvento());
    }

    @Override
    protected void enviar(Notificacion notificacion) {
        LOG.info("[SMTP simulado] Para: " + notificacion.getDestino() + " | Asunto: " + notificacion.getAsunto());
    }
}
