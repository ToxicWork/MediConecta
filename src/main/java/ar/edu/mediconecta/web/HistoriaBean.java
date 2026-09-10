package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.TipoEntrada;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeHistoriaClinica;
import ar.edu.mediconecta.negocio.ServicioDeTurnos;
import jakarta.annotation.PostConstruct;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;

@Named
@ViewScoped
public class HistoriaBean implements Serializable {

    @Inject
    private ServicioDeHistoriaClinica servicioDeHistoriaClinica;

    @Inject
    private ServicioDeTurnos servicioDeTurnos;

    @Inject
    private LoginBean loginBean;

    private Long pacienteId;
    private HistoriaClinica historia;

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
        pacienteId = id;
        historia = servicioDeHistoriaClinica.obtenerDePaciente(id);
    }

    public void agregarEntrada() {
        Usuario actual = loginBean.getUsuarioActual();
        servicioDeHistoriaClinica.agregarEntrada(pacienteId, actual.getId(), turnoAsociadoId, tipoNuevaEntrada, descripcionNuevaEntrada);
        historia = servicioDeHistoriaClinica.obtenerDePaciente(pacienteId);
        descripcionNuevaEntrada = null;
    }

    public List<Turno> getTurnosDelProfesional() {
        Usuario actual = loginBean.getUsuarioActual();
        return actual == null ? List.of() : servicioDeTurnos.buscarConfirmadosDeProfesional(actual.getId());
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
}
