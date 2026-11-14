package ar.edu.mediconecta.web;

import ar.edu.mediconecta.negocio.ServicioDeMonitoreo;
import ar.edu.mediconecta.negocio.dto.FilaMonitorDTO;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.primefaces.model.FilterMeta;
import org.primefaces.model.LazyDataModel;
import org.primefaces.model.SortMeta;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Monitor de eventos (HU-09): el paciente o el profesional ve si el sistema ya procesó los avisos de sus turnos,
 * suscriptor por suscriptor y con su latencia. Sólo los eventos de sus propios turnos (la titularidad la aplica
 * {@link ServicioDeMonitoreo}).
 *
 * <p>La tabla carga de a tandas: un {@link LazyDataModel} le pide al servicio sólo la página visible y el total
 * para el paginador, así la pantalla no crece con el historial. {@code @ViewScoped} porque el paginador cambia de
 * página por AJAX y el modelo tiene que seguir vivo entre esos pedidos.
 */
@Named
@ViewScoped
public class MonitorBean implements Serializable {

    /** Avisos por página. */
    public static final int TAMANO_PAGINA = 10;

    @Inject
    private ServicioDeMonitoreo servicioDeMonitoreo;

    @Inject
    private LoginBean loginBean;

    private final LazyDataModel<FilaMonitorDTO> eventos = new LazyDataModel<>() {

        @Override
        public int count(Map<String, FilterMeta> filtros) {
            return servicioDeMonitoreo.contarEventosDelUsuarioActual();
        }

        @Override
        public List<FilaMonitorDTO> load(int desde, int cantidad, Map<String, SortMeta> orden,
                                         Map<String, FilterMeta> filtros) {
            return servicioDeMonitoreo.eventosDelUsuarioActual(desde, cantidad);
        }

        @Override
        public String getRowKey(FilaMonitorDTO fila) {
            return fila.getIdEvento();
        }
    };

    public String verificarAcceso() {
        return loginBean.exigirRol("PACIENTE", "PROFESIONAL");
    }

    public LazyDataModel<FilaMonitorDTO> getEventos() {
        return eventos;
    }

    public int getTamanoPagina() {
        return TAMANO_PAGINA;
    }
}
