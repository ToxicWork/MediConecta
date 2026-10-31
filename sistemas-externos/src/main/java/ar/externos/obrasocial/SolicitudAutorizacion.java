package ar.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SolicitudAutorizacion", propOrder = {"idSolicitud", "obraSocial", "plan", "nroAfiliado",
        "codigoPrestacion", "fechaPrestacion", "matriculaPrestador"})
public class SolicitudAutorizacion {

    /** Identificador que asigna el prestador: el legado deduplica por este campo (reintentos seguros). */
    @XmlElement(required = true)
    private String idSolicitud;
    @XmlElement(required = true)
    private String obraSocial;
    private String plan;
    private String nroAfiliado;
    @XmlElement(required = true)
    private String codigoPrestacion;
    private String fechaPrestacion;
    private String matriculaPrestador;

    public String getIdSolicitud() {
        return idSolicitud;
    }

    public void setIdSolicitud(String idSolicitud) {
        this.idSolicitud = idSolicitud;
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public void setObraSocial(String obraSocial) {
        this.obraSocial = obraSocial;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public String getNroAfiliado() {
        return nroAfiliado;
    }

    public void setNroAfiliado(String nroAfiliado) {
        this.nroAfiliado = nroAfiliado;
    }

    public String getCodigoPrestacion() {
        return codigoPrestacion;
    }

    public void setCodigoPrestacion(String codigoPrestacion) {
        this.codigoPrestacion = codigoPrestacion;
    }

    public String getFechaPrestacion() {
        return fechaPrestacion;
    }

    public void setFechaPrestacion(String fechaPrestacion) {
        this.fechaPrestacion = fechaPrestacion;
    }

    public String getMatriculaPrestador() {
        return matriculaPrestador;
    }

    public void setMatriculaPrestador(String matriculaPrestador) {
        this.matriculaPrestador = matriculaPrestador;
    }
}
