package ar.edu.mediconecta.notificaciones;

import ar.edu.mediconecta.dao.NotificacionDAO;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.modelo.Notificacion;
import jakarta.inject.Inject;

import java.time.format.DateTimeFormatter;

/**
 * Template Method: {@link #procesar} fija el orden de los pasos (armar, enviar, registrar) para
 * cualquier canal. El paso de creación es un Factory Method ({@link #crearNotificacion}) que
 * cada subclase redefine según su canal y destinatario.
 *
 * <p>Es {@code @Dependent} (sin proxy de CDI) porque los beans con scope normal no admiten
 * métodos {@code final}.
 */
public abstract class ProcesadorNotificacion {

    protected static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'hs'");

    @Inject
    private NotificacionDAO notificacionDAO;

    public final void procesar(EventoTurno evento) {
        Notificacion notificacion = crearNotificacion(evento);
        if (notificacion == null) {
            return;
        }
        enviar(notificacion);
        notificacionDAO.guardar(notificacion);
    }

    /** Factory Method: devuelve {@code null} si este canal no aplica al evento. */
    protected abstract Notificacion crearNotificacion(EventoTurno evento);

    protected abstract void enviar(Notificacion notificacion);
}
