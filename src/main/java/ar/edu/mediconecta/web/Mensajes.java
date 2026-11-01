package ar.edu.mediconecta.web;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;

/** Mensajes al usuario (growl). Los que preceden a una redirección se guardan en el flash para que lleguen. */
final class Mensajes {

    private Mensajes() {
    }

    static void info(String mensaje) {
        agregar(FacesMessage.SEVERITY_INFO, mensaje, false);
    }

    static void error(String mensaje) {
        agregar(FacesMessage.SEVERITY_ERROR, mensaje, false);
    }

    /** Para mostrar en la página a la que se redirige ({@code faces-redirect=true}). */
    static void infoTrasRedireccion(String mensaje) {
        agregar(FacesMessage.SEVERITY_INFO, mensaje, true);
    }

    private static void agregar(FacesMessage.Severity severidad, String mensaje, boolean conservar) {
        FacesContext contexto = FacesContext.getCurrentInstance();
        if (conservar) {
            contexto.getExternalContext().getFlash().setKeepMessages(true);
        }
        contexto.addMessage(null, new FacesMessage(severidad, mensaje, null));
    }
}
