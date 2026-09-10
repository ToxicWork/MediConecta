package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeTurnos;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import ar.edu.mediconecta.negocio.excepciones.TurnoNoDisponibleException;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Named
@SessionScoped
public class AgendaBean implements Serializable {

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Inject
    private ServicioDeTurnos servicioDeTurnos;

    @Inject
    private LoginBean loginBean;

    private Long profesionalSeleccionadoId;
    private List<Turno> disponibilidad = new ArrayList<>();
    private Turno turnoEnHold;
    private LocalDateTime nuevaFechaHora;

    public String verificarAcceso() {
        return loginBean.getUsuarioActual() == null ? "login.xhtml?faces-redirect=true" : null;
    }

    public List<Profesional> getProfesionales() {
        return servicioDeUsuarios.listarProfesionales();
    }

    public void buscarDisponibilidad() {
        if (profesionalSeleccionadoId != null) {
            disponibilidad = servicioDeTurnos.buscarDisponibilidad(profesionalSeleccionadoId);
        }
    }

    public void reservarHold(Long turnoId) {
        try {
            Usuario usuario = loginBean.getUsuarioActual();
            turnoEnHold = servicioDeTurnos.reservarHold(turnoId, usuario.getId());
            buscarDisponibilidad();
        } catch (TurnoNoDisponibleException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    public void confirmar() {
        Turno confirmado = servicioDeTurnos.confirmar();
        turnoEnHold = null;
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Turno confirmado para " + confirmado.getFechaHora(), null));
    }

    public void publicarDisponibilidad() {
        Usuario actual = loginBean.getUsuarioActual();
        servicioDeTurnos.publicarDisponibilidad(actual.getId(), nuevaFechaHora);
        nuevaFechaHora = null;
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_INFO, "Disponibilidad publicada", null));
    }

    public Long getProfesionalSeleccionadoId() {
        return profesionalSeleccionadoId;
    }

    public void setProfesionalSeleccionadoId(Long profesionalSeleccionadoId) {
        this.profesionalSeleccionadoId = profesionalSeleccionadoId;
    }

    public List<Turno> getDisponibilidad() {
        return disponibilidad;
    }

    public Turno getTurnoEnHold() {
        return turnoEnHold;
    }

    public LocalDateTime getNuevaFechaHora() {
        return nuevaFechaHora;
    }

    public void setNuevaFechaHora(LocalDateTime nuevaFechaHora) {
        this.nuevaFechaHora = nuevaFechaHora;
    }
}
