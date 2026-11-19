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

    private static final String TURNOS_DEL_PROFESIONAL = TelemedicinaBean.class.getName() + ".turnosDelProfesional";

    private List<Turno> turnos;

    public String verificarAcceso() {
        return loginBean.exigirRol("PACIENTE", "PROFESIONAL");
    }

    public List<Turno> getTurnos() {
        if (turnos == null) {
            // El profesional finaliza la consulta con un botón con confirmación: la lista del envío tiene que ser la
            // que vio (D-45). La del paciente no, porque se refresca sola cada 5 segundos y sus botones no cambian nada.
            turnos = loginBean.isProfesional()
                    ? ListasDeLaVista.de(TURNOS_DEL_PROFESIONAL, servicioDeTelemedicina::turnosVirtualesDelUsuarioActual)
                    : servicioDeTelemedicina.turnosVirtualesDelUsuarioActual();
        }
        return turnos;
    }

    public void iniciar(Long turnoId) {
        try {
            servicioDeTelemedicina.iniciarVideoconsulta(turnoId);
            turnos = null;
            ListasDeLaVista.olvidar(TURNOS_DEL_PROFESIONAL);
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Abriste la sala. Ya podés entrar; le avisamos a tu paciente.", null));
        } catch (ErrorDeNegocioException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    /** D-14: el profesional termina la consulta y cierra la sala. */
    public void finalizar(Long turnoId) {
        try {
            Turno turno = servicioDeTelemedicina.finalizarVideoconsulta(turnoId);
            turnos = null;
            ListasDeLaVista.olvidar(TURNOS_DEL_PROFESIONAL);
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO,
                    "Finalizaste la videoconsulta con " + turno.getPaciente().getNombreCompleto() + ". La sala quedó cerrada.", null));
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
