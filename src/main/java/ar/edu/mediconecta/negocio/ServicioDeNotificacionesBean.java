package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.NotificacionDAO;
import ar.edu.mediconecta.modelo.Notificacion;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Consulta de la bandeja de notificaciones. Las notificaciones las genera de forma asincrónica
 * {@code NotificacionesSubscriber} al consumir {@code topico.turnos}; este bean sólo las expone
 * a la capa de presentación. Sin estado conversacional, por eso es {@code @Stateless}.
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL"})
@RolesAllowed({"PACIENTE", "PROFESIONAL"})
public class ServicioDeNotificacionesBean implements ServicioDeNotificaciones {

    @Inject
    private NotificacionDAO notificacionDAO;

    @Override
    public List<Notificacion> listarDeUsuario(Long usuarioId) {
        return notificacionDAO.buscarDeUsuario(usuarioId);
    }

    @Override
    public long contarNoLeidas(Long usuarioId) {
        return notificacionDAO.contarNoLeidas(usuarioId);
    }

    @Override
    public void marcarTodasLeidas(Long usuarioId) {
        notificacionDAO.marcarLeidas(usuarioId);
    }
}
