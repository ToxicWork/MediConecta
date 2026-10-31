package ar.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ResultadoAutorizacion", propOrder = {"nroTransaccion", "idSolicitud", "estado", "codigoAutorizacion", "motivo"})
public class ResultadoAutorizacion {

    @XmlElement(required = true)
    private String nroTransaccion;
    @XmlElement(required = true)
    private String idSolicitud;
    @XmlElement(required = true)
    private EstadoAutorizacion estado;
    private String codigoAutorizacion;
    private String motivo;

    public ResultadoAutorizacion() {
    }

    public ResultadoAutorizacion(String nroTransaccion, String idSolicitud, EstadoAutorizacion estado,
                                 String codigoAutorizacion, String motivo) {
        this.nroTransaccion = nroTransaccion;
        this.idSolicitud = idSolicitud;
        this.estado = estado;
        this.codigoAutorizacion = codigoAutorizacion;
        this.motivo = motivo;
    }

    public String getNroTransaccion() {
        return nroTransaccion;
    }

    public String getIdSolicitud() {
        return idSolicitud;
    }

    public EstadoAutorizacion getEstado() {
        return estado;
    }

    public String getCodigoAutorizacion() {
        return codigoAutorizacion;
    }

    public String getMotivo() {
        return motivo;
    }
}
