package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PACIENTE")
public class Paciente extends Usuario {

    @Column(name = "obra_social")
    private String obraSocial;

    @Column(name = "nro_afiliado", length = 40)
    private String nroAfiliado;

    protected Paciente() {
    }

    public Paciente(String nombreCompleto, String email, String password, String obraSocial) {
        this(nombreCompleto, email, password, obraSocial, null);
    }

    public Paciente(String nombreCompleto, String email, String password, String obraSocial, String nroAfiliado) {
        super(nombreCompleto, email, password, Rol.PACIENTE);
        this.obraSocial = obraSocial;
        this.nroAfiliado = nroAfiliado;
    }

    public boolean tieneObraSocial() {
        return obraSocial != null && !obraSocial.isBlank();
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public void setObraSocial(String obraSocial) {
        this.obraSocial = obraSocial;
    }

    public String getNroAfiliado() {
        return nroAfiliado;
    }

    public void setNroAfiliado(String nroAfiliado) {
        this.nroAfiliado = nroAfiliado;
    }
}
