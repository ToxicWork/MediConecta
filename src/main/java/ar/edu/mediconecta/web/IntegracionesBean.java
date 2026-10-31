package ar.edu.mediconecta.web;

import ar.edu.mediconecta.integracion.ConfiguracionIntegraciones;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.modelo.LlamadaIntegracion;
import ar.edu.mediconecta.negocio.InyectorDeFallas;
import ar.edu.mediconecta.negocio.ServicioDeMonitoreo;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

/** Panel de integraciones sincrónicas: estado de los Circuit Breakers, bitácora SOAP/REST y la demo transaccional. */
@Named
@RequestScoped
public class IntegracionesBean {

    @Inject
    private ServicioDeMonitoreo servicioDeMonitoreo;

    @Inject
    private InyectorDeFallas inyectorDeFallas;

    @Inject
    private ConfiguracionIntegraciones config;

    @Inject
    private LoginBean loginBean;

    private List<EstadoCircuito> circuitos;
    private List<LlamadaIntegracion> llamadas;

    public String verificarAcceso() {
        return loginBean.getUsuarioActual() == null ? "login.xhtml?faces-redirect=true" : null;
    }

    public List<EstadoCircuito> getCircuitos() {
        if (circuitos == null) {
            circuitos = servicioDeMonitoreo.circuitos();
        }
        return circuitos;
    }

    public List<LlamadaIntegracion> getLlamadas() {
        if (llamadas == null) {
            llamadas = servicioDeMonitoreo.llamadasRecientes();
        }
        return llamadas;
    }

    public boolean isFallaArmada() {
        return inyectorDeFallas.isArmada();
    }

    public void armarFalla() {
        inyectorDeFallas.armar();
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
                "Falla armada: la próxima confirmación de turno va a revertirse antes del commit.", null));
    }

    public void desarmarFalla() {
        inyectorDeFallas.desarmar();
    }

    public String getUrlWsdl() {
        return config.urlWsdlObraSocial();
    }

    public String getUrlConsola() {
        return config.urlExternos() + "/";
    }

    public String getUrlPasarela() {
        return config.urlPasarela() + "/pagos";
    }

    public String getUrlVideoconsulta() {
        return config.urlVideoconsulta() + "/salas";
    }

    public String estiloResultado(String resultado) {
        return switch (resultado) {
            case "OK" -> "tag-success";
            case "RECHAZO", "CACHE", "PENDIENTE" -> "tag-warning";
            case "COMPENSACION" -> "tag-info";
            default -> "tag-danger";
        };
    }

    public String estiloCircuito(String estado) {
        return switch (estado) {
            case "CERRADO" -> "tag-success";
            case "SEMIABIERTO" -> "tag-warning";
            default -> "tag-danger";
        };
    }
}
