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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Named
@ViewScoped
public class HistoriaBean implements Serializable {

    private static final String TURNOS_DEL_PROFESIONAL = HistoriaBean.class.getName() + ".turnosDelProfesional";
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
        return loginBean.exigirRol("PACIENTE", "PROFESIONAL");
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

    /**
     * Turnos confirmados del profesional (también los pasados: habilitan la historia clínica). Se evalúa varias veces
     * por página, así que se guarda en el request.
     */
    @SuppressWarnings("unchecked")
    public List<Turno> getTurnosDelProfesional() {
        Usuario actual = loginBean.getUsuarioActual();
        if (actual == null || !loginBean.isProfesional()) {
            return List.of();
        }
        Map<String, Object> request = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
        return (List<Turno>) request.computeIfAbsent(TURNOS_DEL_PROFESIONAL,
                clave -> servicioDeTurnos.buscarConfirmadosDeProfesional(actual.getId()));
    }

    /** Pacientes con algún turno confirmado con el profesional, una sola vez cada uno y por nombre (D-15). */
    public List<Paciente> getPacientesDelProfesional() {
        Map<Long, Paciente> unicos = new LinkedHashMap<>();
        for (Turno turno : getTurnosDelProfesional()) {
            unicos.putIfAbsent(turno.getPaciente().getId(), turno.getPaciente());
        }
        return unicos.values().stream()
                .sorted(Comparator.comparing(Paciente::getNombreCompleto, String.CASE_INSENSITIVE_ORDER))
                .toList();
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
        return getPacientesDelProfesional().stream()
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
