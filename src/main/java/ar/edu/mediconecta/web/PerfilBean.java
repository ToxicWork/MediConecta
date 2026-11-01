package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.PoliticaDeContrasenas;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * "Mi perfil": los datos del usuario (sólo lectura) y el cambio de la propia contraseña. Es la única pantalla
 * disponible mientras el usuario tenga una contraseña temporal.
 */
@Named
@RequestScoped
public class PerfilBean {

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Inject
    private LoginBean loginBean;

    private String actual;
    private String nueva;
    private String repeticion;

    public String verificarAcceso() {
        return loginBean.exigirSesion();
    }

    public String cambiarPassword() {
        boolean eraObligatorio = loginBean.isDebeCambiarPassword();
        try {
            servicioDeUsuarios.cambiarMiPassword(actual, nueva, repeticion);
        } catch (ErrorDeNegocioException e) {
            Mensajes.error(e.getMessage());
            return null;
        }
        loginBean.olvidarUsuarioDelRequest();
        if (eraObligatorio) {
            Mensajes.infoTrasRedireccion("Listo, ya podés usar MediConecta con tu nueva contraseña.");
            return loginBean.getPaginaDeInicio() + "?faces-redirect=true";
        }
        Mensajes.infoTrasRedireccion("Cambiaste tu contraseña.");
        return LoginBean.PERFIL + "?faces-redirect=true";
    }

    public Paciente getPaciente() {
        Usuario usuario = loginBean.getUsuarioActual();
        return usuario instanceof Paciente paciente ? paciente : null;
    }

    public Profesional getProfesional() {
        Usuario usuario = loginBean.getUsuarioActual();
        return usuario instanceof Profesional profesional ? profesional : null;
    }

    public int getLongitudMinima() {
        return PoliticaDeContrasenas.LONGITUD_MINIMA;
    }

    public String getActual() {
        return actual;
    }

    public void setActual(String actual) {
        this.actual = actual;
    }

    public String getNueva() {
        return nueva;
    }

    public void setNueva(String nueva) {
        this.nueva = nueva;
    }

    public String getRepeticion() {
        return repeticion;
    }

    public void setRepeticion(String repeticion) {
        this.repeticion = repeticion;
    }
}
