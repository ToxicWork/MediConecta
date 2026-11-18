package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.Notificacion;
import jakarta.ejb.Local;

import java.util.List;

@Local
public interface ServicioDeNotificaciones {

    List<Notificacion> listarDeUsuario(Long usuarioId);

    long contarNoLeidas(Long usuarioId);

    void marcarTodasLeidas(Long usuarioId);

    /** Marca como leída una notificación del usuario; una ajena o inexistente no cambia nada (devuelve false). */
    boolean marcarLeida(Long usuarioId, Long notificacionId);
}
