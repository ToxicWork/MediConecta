package ar.edu.mediconecta.integracion.obrasocial;

import ar.edu.mediconecta.modelo.EstadoCobertura;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Respuesta de cobertura en términos del dominio de MediConecta. El adaptador SOAP traduce a esta clase
 * los tipos generados por wsimport, que nunca salen del paquete de integración.
 */
public class ResultadoCobertura implements Serializable {

    private final EstadoCobertura estado;
    private final TipoCopago tipoCopago;
    private final BigDecimal valorCopago;
    private final String plan;
    private final boolean requiereAutorizacion;
    private final String mensaje;
    private final OrigenCobertura origen;
    private final String nroTransaccion;

    public ResultadoCobertura(EstadoCobertura estado, TipoCopago tipoCopago, BigDecimal valorCopago, String plan,
                              boolean requiereAutorizacion, String mensaje, OrigenCobertura origen, String nroTransaccion) {
        this.estado = estado;
        this.tipoCopago = tipoCopago == null ? TipoCopago.NINGUNO : tipoCopago;
        this.valorCopago = valorCopago;
        this.plan = plan;
        this.requiereAutorizacion = requiereAutorizacion;
        this.mensaje = mensaje;
        this.origen = origen;
        this.nroTransaccion = nroTransaccion;
    }

    public static ResultadoCobertura sinObraSocial() {
        return new ResultadoCobertura(EstadoCobertura.PARTICULAR, TipoCopago.NINGUNO, null, null, false,
                "No declaraste obra social: el turno se abona como particular.", OrigenCobertura.SIN_OBRA_SOCIAL, null);
    }

    /** Fallback sin caché: no se sabe si hay cobertura; la autorización asincrónica lo resolverá. */
    public static ResultadoCobertura pendiente(String motivo) {
        return new ResultadoCobertura(EstadoCobertura.PENDIENTE, TipoCopago.NINGUNO, null, null, true,
                "La obra social no responde (" + motivo + "). Confirmamos el turno sin cobro y la cobertura "
                        + "se resolverá apenas el sistema vuelva.", OrigenCobertura.SIN_RESPUESTA, null);
    }

    public ResultadoCobertura desdeCache() {
        return new ResultadoCobertura(estado, tipoCopago, valorCopago, plan, requiereAutorizacion, mensaje,
                OrigenCobertura.CACHE, nroTransaccion);
    }

    public String resumen() {
        return estado + (tipoCopago == TipoCopago.NINGUNO ? "" : " · " + tipoCopago + (valorCopago == null ? "" : " " + valorCopago))
                + (plan == null ? "" : " · plan " + plan) + (nroTransaccion == null ? "" : " · trx " + nroTransaccion);
    }

    public boolean isCubierta() {
        return estado == EstadoCobertura.CUBIERTA;
    }

    public boolean isPendiente() {
        return estado == EstadoCobertura.PENDIENTE;
    }

    public EstadoCobertura getEstado() {
        return estado;
    }

    public TipoCopago getTipoCopago() {
        return tipoCopago;
    }

    public BigDecimal getValorCopago() {
        return valorCopago;
    }

    public String getPlan() {
        return plan;
    }

    public boolean isRequiereAutorizacion() {
        return requiereAutorizacion;
    }

    public String getMensaje() {
        return mensaje;
    }

    public OrigenCobertura getOrigen() {
        return origen;
    }

    public String getNroTransaccion() {
        return nroTransaccion;
    }
}
