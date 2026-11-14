package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.ProcesoCancelacionTurno;
import ar.edu.mediconecta.negocio.ServicioDeAgenda;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;

import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Pantalla Turnos del administrativo: los próximos turnos confirmados de todos los profesionales (o de uno), para
 * cancelarlos cuando el profesional no puede atender. Carga de a tandas (LazyDataModel + paginador), como el
 * Monitor de eventos. La cancelación es la misma que usan el paciente, el profesional y la API
 * ({@link ProcesoCancelacionTurno}): reintegro automático, aviso al paciente y al profesional, y el horario no se
 * vuelve a ofrecer.
 */
@Named
@ViewScoped
public class TurnosAdminBean implements Serializable {

    public static final int TAMANO_PAGINA = 10;
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Inject
    private ServicioDeAgenda servicioDeAgenda;

    @Inject
    private ProcesoCancelacionTurno procesoCancelacion;

    @Inject
    private LoginBean loginBean;

    /** Filtro: {@code null} = todos los profesionales. */
    private Long profesionalId;

    private transient List<Profesional> profesionales;

    private final LazyDataModel<Turno> turnos = new LazyDataModel<>() {

        @Override
        public int count(Map<String, FilterMeta> filtros) {
            return servicioDeAgenda.contarProximosConfirmados(profesionalId);
        }

        @Override
        public List<Turno> load(int desde, int cantidad, Map<String, SortMeta> orden, Map<String, FilterMeta> filtros) {
            return servicioDeAgenda.proximosConfirmados(profesionalId, desde, cantidad);
        }

        @Override
        public String getRowKey(Turno turno) {
            return String.valueOf(turno.getId());
        }
    };

    public String verificarAcceso() {
        return loginBean.exigirRol("ADMINISTRATIVO");
    }

    public void cancelar(Long turnoId) {
        try {
            Turno cancelado = procesoCancelacion.cancelar(turnoId);
            boolean habiaPago = cancelado.getImportePaciente() != null && cancelado.getImportePaciente().signum() > 0;
            Mensajes.info("Cancelaste el turno de " + cancelado.getPaciente().getNombreCompleto() + " con "
                    + cancelado.getProfesional().getNombreCompleto() + " del " + formatear(cancelado) + ". Les avisamos"
                    + " al paciente y al profesional" + (habiaPago ? "; el reintegro de $ "
                    + cancelado.getImportePaciente().toPlainString() + " se procesa en unos segundos" : "")
                    + ". El horario no se vuelve a ofrecer.");
        } catch (ErrorDeNegocioException e) {
            Mensajes.error(e.getMessage());
        }
    }

    public String formatear(Turno turno) {
        return FORMATO_FECHA.format(turno.getFechaHora());
    }

    public List<Profesional> getProfesionales() {
        if (profesionales == null) {
            profesionales = servicioDeAgenda.profesionales();
        }
        return profesionales;
    }

    public LazyDataModel<Turno> getTurnos() {
        return turnos;
    }

    public int getTamanoPagina() {
        return TAMANO_PAGINA;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public void setProfesionalId(Long profesionalId) {
        this.profesionalId = profesionalId;
    }
}
