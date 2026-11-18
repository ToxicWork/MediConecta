package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.EstadoPago;
import ar.edu.mediconecta.modelo.EstadoReclamo;
import ar.edu.mediconecta.modelo.Pago;
import ar.edu.mediconecta.modelo.ReclamoObraSocial;
import ar.edu.mediconecta.negocio.ControlDeDemostracion;
import ar.edu.mediconecta.negocio.ServicioDeFacturacion;
import ar.edu.mediconecta.negocio.ServicioDePagos;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.math.BigDecimal;
import java.util.List;

/** Pagos (pasarela REST) del paciente y, para el administrativo, todos los cobros y reclamos a obras sociales. */
@Named
@RequestScoped
public class PagosBean {

    @Inject
    private ServicioDePagos servicioDePagos;

    @Inject
    private ServicioDeFacturacion servicioDeFacturacion;

    @Inject
    private ControlDeDemostracion demostracion;

    @Inject
    private LoginBean loginBean;

    private List<Pago> pagos;
    private List<ReclamoObraSocial> reclamos;

    public String verificarAcceso() {
        return loginBean.exigirRol("PACIENTE", "ADMINISTRATIVO");
    }

    public List<Pago> getPagos() {
        if (pagos == null) {
            pagos = loginBean.isAdministrativo() ? servicioDePagos.todosLosPagos()
                    : loginBean.isPaciente() ? servicioDePagos.pagosDelUsuarioActual() : List.of();
        }
        return pagos;
    }

    public List<ReclamoObraSocial> getReclamos() {
        if (reclamos == null) {
            reclamos = loginBean.isAdministrativo() ? servicioDeFacturacion.todosLosReclamos() : List.of();
        }
        return reclamos;
    }

    public BigDecimal getTotalAprobado() {
        return getPagos().stream().filter(p -> p.getEstado() == EstadoPago.APROBADO)
                .map(Pago::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalReembolsado() {
        return getPagos().stream().filter(p -> p.getEstado() == EstadoPago.REEMBOLSADO)
                .map(Pago::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal getTotalReclamado() {
        return getReclamos().stream().filter(r -> r.getEstado() == EstadoReclamo.PENDIENTE_PRESENTACION)
                .map(ReclamoObraSocial::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Falla de demostración del flujo crítico (sólo administrativo; el rol lo exige {@link ControlDeDemostracion}).
     * Armada, la próxima confirmación de turno falla antes del commit: se ve el rollback y el reembolso del cobro.
     */
    public boolean isFallaArmada() {
        return loginBean.isAdministrativo() && demostracion.isFallaArmada();
    }

    /** El panel de la falla sólo aparece si el servidor la habilitó para una demostración. */
    public boolean isFallaHabilitada() {
        return loginBean.isAdministrativo() && demostracion.isFallaHabilitada();
    }

    public void armarFalla() {
        try {
            demostracion.armarFalla();
            FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_WARN,
                    "Falla armada: la próxima confirmación de turno va a revertirse antes del commit.", null));
        } catch (ErrorDeNegocioException apagada) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, apagada.getMessage(), null));
        }
    }

    public void desarmarFalla() {
        demostracion.desarmarFalla();
    }
}
