package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.Notificacion;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeNotificaciones;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

@Named
@RequestScoped
public class NotificacionesBean {

    @Inject
    private ServicioDeNotificaciones servicioDeNotificaciones;

    @Inject
    private LoginBean loginBean;

    private List<Notificacion> notificaciones;
    private Long noLeidas;

    public String verificarAcceso() {
        return loginBean.getUsuarioActual() == null ? "login.xhtml?faces-redirect=true" : null;
    }

    public List<Notificacion> getNotificaciones() {
        if (notificaciones == null) {
            Usuario usuario = loginBean.getUsuarioActual();
            notificaciones = usuario == null ? List.of() : servicioDeNotificaciones.listarDeUsuario(usuario.getId());
        }
        return notificaciones;
    }

    public long getNoLeidas() {
        if (noLeidas == null) {
            Usuario usuario = loginBean.getUsuarioActual();
            noLeidas = usuario == null ? 0L : servicioDeNotificaciones.contarNoLeidas(usuario.getId());
        }
        return noLeidas;
    }

    public void marcarTodasLeidas() {
        Usuario usuario = loginBean.getUsuarioActual();
        if (usuario != null) {
            servicioDeNotificaciones.marcarTodasLeidas(usuario.getId());
        }
        notificaciones = null;
        noLeidas = null;
    }
}
