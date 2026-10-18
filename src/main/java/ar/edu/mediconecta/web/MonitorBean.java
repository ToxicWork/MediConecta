package ar.edu.mediconecta.web;

import ar.edu.mediconecta.negocio.ServicioDeMonitoreo;
import ar.edu.mediconecta.negocio.dto.FilaMonitorDTO;
import ar.edu.mediconecta.negocio.dto.ResumenMonitorDTO;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

@Named
@RequestScoped
public class MonitorBean {

    @Inject
    private ServicioDeMonitoreo servicioDeMonitoreo;

    @Inject
    private LoginBean loginBean;

    private List<FilaMonitorDTO> filas;
    private ResumenMonitorDTO resumen;

    public String verificarAcceso() {
        return loginBean.getUsuarioActual() == null ? "login.xhtml?faces-redirect=true" : null;
    }

    public List<FilaMonitorDTO> getFilas() {
        if (filas == null) {
            filas = servicioDeMonitoreo.eventosRecientesDelUsuarioActual();
        }
        return filas;
    }

    public ResumenMonitorDTO getResumen() {
        if (resumen == null) {
            resumen = ResumenMonitorDTO.de(getFilas());
        }
        return resumen;
    }
}
