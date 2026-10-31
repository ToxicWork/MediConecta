package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.ServicioDeTelemedicina;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

@Named
@RequestScoped
public class TelemedicinaBean {

    @Inject
    private ServicioDeTelemedicina servicioDeTelemedicina;

    @Inject
    private LoginBean loginBean;

    private List<Turno> turnos;

    public String verificarAcceso() {
        return loginBean.getUsuarioActual() == null ? "login.xhtml?faces-redirect=true" : null;
    }

    public List<Turno> getTurnos() {
        if (turnos == null) {
            turnos = servicioDeTelemedicina.turnosVirtualesDelUsuarioActual();
        }
        return turnos;
    }

    public void iniciar(Long turnoId) {
        try {
            servicioDeTelemedicina.iniciarVideoconsulta(turnoId);
            turnos = null;
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Sala creada en el proveedor de videoconsulta. Ya podés entrar; el paciente ve el enlace en su pantalla.", null));
        } catch (ErrorDeNegocioException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    /** Enlace a la sala según quién mira: cada rol entra con su propia URL. */
    public String enlace(Turno turno) {
        return loginBean.isProfesional() ? turno.getSalaUrlProfesional() : turno.getSalaUrlPaciente();
    }
}
