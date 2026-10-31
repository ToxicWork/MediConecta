package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.TipoEntrada;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeHistoriaClinica;
import ar.edu.mediconecta.negocio.ServicioDeTurnos;
import ar.edu.mediconecta.negocio.dto.AccesoHistoriaDTO;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Named
@ViewScoped
public class HistoriaBean implements Serializable {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Inject
    private ServicioDeHistoriaClinica servicioDeHistoriaClinica;

    @Inject
    private ServicioDeTurnos servicioDeTurnos;

    @Inject
    private LoginBean loginBean;

    private Long pacienteId;
    private HistoriaClinica historia;
    private List<AccesoHistoriaDTO> accesos;

    private TipoEntrada tipoNuevaEntrada = TipoEntrada.DIAGNOSTICO;
    private String descripcionNuevaEntrada;
    private Long turnoAsociadoId;

    @PostConstruct
    public void init() {
        Usuario actual = loginBean.getUsuarioActual();
        if (actual instanceof Paciente) {
            pacienteId = actual.getId();
            historia = servicioDeHistoriaClinica.obtenerDePaciente(pacienteId);
        }
    }

    public String verificarAcceso() {
        return loginBean.getUsuarioActual() == null ? "login.xhtml?faces-redirect=true" : null;
    }

    public void cargarHistoriaDePaciente(Long id) {
        if (id == null) {
            return;
        }
        try {
            pacienteId = id;
            historia = servicioDeHistoriaClinica.obtenerDePaciente(id);
        } catch (ErrorDeNegocioException e) {
            historia = null;
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    public void agregarEntrada() {
        try {
            servicioDeHistoriaClinica.agregarEntrada(pacienteId, turnoAsociadoId, tipoNuevaEntrada, descripcionNuevaEntrada);
            historia = servicioDeHistoriaClinica.obtenerDePaciente(pacienteId);
            descripcionNuevaEntrada = null;
        } catch (ErrorDeNegocioException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
        }
    }

    /** Sólo para el paciente: quién consultó o modificó su historia (registrado por el interceptor de auditoría). */
    public List<AccesoHistoriaDTO> getAccesos() {
        if (accesos == null) {
            accesos = loginBean.isPaciente() ? servicioDeHistoriaClinica.accesosAMiHistoria() : List.of();
        }
        return accesos;
    }

    public List<Turno> getTurnosDelProfesional() {
        Usuario actual = loginBean.getUsuarioActual();
        return actual == null || !loginBean.isProfesional() ? List.of()
                : servicioDeTurnos.buscarConfirmadosDeProfesional(actual.getId());
    }

    public String getNombrePacienteSeleccionado() {
        Usuario actual = loginBean.getUsuarioActual();
        if (actual instanceof Paciente) {
            return actual.getNombreCompleto();
        }
        return buscarPacienteSeleccionado().map(Paciente::getNombreCompleto).orElse(null);
    }

    public String getObraSocialPacienteSeleccionado() {
        Usuario actual = loginBean.getUsuarioActual();
        if (actual instanceof Paciente paciente) {
            return paciente.getObraSocial();
        }
        return buscarPacienteSeleccionado().map(Paciente::getObraSocial).orElse(null);
    }

    private Optional<Paciente> buscarPacienteSeleccionado() {
        if (pacienteId == null) {
            return Optional.empty();
        }
        return getTurnosDelProfesional().stream()
                .map(Turno::getPaciente)
                .filter(paciente -> pacienteId.equals(paciente.getId()))
                .findFirst();
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public void setPacienteId(Long pacienteId) {
        this.pacienteId = pacienteId;
    }

    public HistoriaClinica getHistoria() {
        return historia;
    }

    public TipoEntrada getTipoNuevaEntrada() {
        return tipoNuevaEntrada;
    }

    public void setTipoNuevaEntrada(TipoEntrada tipoNuevaEntrada) {
        this.tipoNuevaEntrada = tipoNuevaEntrada;
    }

    public String getDescripcionNuevaEntrada() {
        return descripcionNuevaEntrada;
    }

    public void setDescripcionNuevaEntrada(String descripcionNuevaEntrada) {
        this.descripcionNuevaEntrada = descripcionNuevaEntrada;
    }

    public Long getTurnoAsociadoId() {
        return turnoAsociadoId;
    }

    public void setTurnoAsociadoId(Long turnoAsociadoId) {
        this.turnoAsociadoId = turnoAsociadoId;
    }

    /** Fecha legible para las etiquetas de los selects, donde no se puede usar un converter. */
    public String formatear(LocalDateTime fechaHora) {
        return fechaHora == null ? "" : FORMATO_FECHA.format(fechaHora);
    }
}
