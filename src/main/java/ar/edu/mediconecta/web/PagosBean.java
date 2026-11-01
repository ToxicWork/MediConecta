package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.EstadoPago;
import ar.edu.mediconecta.modelo.EstadoReclamo;
import ar.edu.mediconecta.modelo.Pago;
import ar.edu.mediconecta.modelo.ReclamoObraSocial;
import ar.edu.mediconecta.negocio.ServicioDeFacturacion;
import ar.edu.mediconecta.negocio.ServicioDePagos;
import jakarta.enterprise.context.RequestScoped;
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
}
