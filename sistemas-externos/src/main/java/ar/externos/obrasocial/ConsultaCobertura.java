package ar.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ConsultaCobertura", propOrder = {"obraSocial", "plan", "nroAfiliado", "codigoPrestacion", "fechaPrestacion"})
public class ConsultaCobertura {

    @XmlElement(required = true)
    private String obraSocial;
    private String plan;
    private String nroAfiliado;
    @XmlElement(required = true)
    private String codigoPrestacion;
    /** Formato aaaa-mm-ddThh:mm, como lo maneja el legado. */
    private String fechaPrestacion;

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
}
