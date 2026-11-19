package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.EstadoPago;
import ar.edu.mediconecta.modelo.EstadoReclamo;
import ar.edu.mediconecta.modelo.Pago;
import ar.edu.mediconecta.modelo.ReclamoObraSocial;
import ar.edu.mediconecta.modelo.ReintegroPendiente;
import ar.edu.mediconecta.negocio.ServicioDeFacturacion;
import ar.edu.mediconecta.negocio.ServicioDePagos;
import ar.edu.mediconecta.negocio.ServicioDeReintegros;
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
    private ServicioDeReintegros servicioDeReintegros;

    @Inject
    private LoginBean loginBean;

    private List<Pago> pagos;
    private List<ReclamoObraSocial> reclamos;
    private List<ReintegroPendiente> reintegros;

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

    /** D-06 y D-41: reintegros que la pasarela no pudo hacer en el momento (sólo el administrativo). */
    public List<ReintegroPendiente> getReintegros() {
        if (reintegros == null) {
            reintegros = loginBean.isAdministrativo() ? servicioDeReintegros.abiertos() : List.of();
        }
        return reintegros;
    }

    /** El id llega con el botón (f:param): la lista se refresca sola cada 5 segundos (D-45). */
    public void reintentarReintegro(Long id) {
        try {
            mensaje(FacesMessage.SEVERITY_INFO, servicioDeReintegros.reintentarAhora(id));
        } catch (ErrorDeNegocioException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
        refrescar();
    }

    public void conciliarReintegro(Long id) {
        try {
            servicioDeReintegros.marcarConciliado(id);
            mensaje(FacesMessage.SEVERITY_INFO, "Marcaste el reintegro como conciliado a mano.");
        } catch (ErrorDeNegocioException e) {
            mensaje(FacesMessage.SEVERITY_ERROR, e.getMessage());
        }
        refrescar();
    }

    private void refrescar() {
        reintegros = null;
        pagos = null;
    }

    private static void mensaje(FacesMessage.Severity severidad, String texto) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(severidad, texto, null));
    }
}
