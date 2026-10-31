package ar.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.math.BigDecimal;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ResultadoCobertura", propOrder = {"nroTransaccion", "estado", "plan", "tipoCopago", "valorCopago",
        "requiereAutorizacion", "mensaje"})
public class ResultadoCobertura {

    @XmlElement(required = true)
    private String nroTransaccion;
    @XmlElement(required = true)
    private EstadoCobertura estado;
    private String plan;
    @XmlElement(required = true)
    private TipoCopago tipoCopago;
    private BigDecimal valorCopago;
    private boolean requiereAutorizacion;
    private String mensaje;

    public ResultadoCobertura() {
    }

    public ResultadoCobertura(String nroTransaccion, EstadoCobertura estado, String plan, TipoCopago tipoCopago,
                              BigDecimal valorCopago, boolean requiereAutorizacion, String mensaje) {
        this.nroTransaccion = nroTransaccion;
        this.estado = estado;
        this.plan = plan;
        this.tipoCopago = tipoCopago;
        this.valorCopago = valorCopago;
        this.requiereAutorizacion = requiereAutorizacion;
        this.mensaje = mensaje;
    }

    public String getNroTransaccion() {
        return nroTransaccion;
    }

    public EstadoCobertura getEstado() {
        return estado;
    }

    public String getPlan() {
        return plan;
    }

    public TipoCopago getTipoCopago() {
        return tipoCopago;
    }

    public BigDecimal getValorCopago() {
        return valorCopago;
    }

    public boolean isRequiereAutorizacion() {
        return requiereAutorizacion;
    }

    public String getMensaje() {
        return mensaje;
    }
}
