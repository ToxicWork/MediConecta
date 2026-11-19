package ar.edu.mediconecta.modelo;

import ar.edu.mediconecta.seguridad.CifradoConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PACIENTE")
public class Paciente extends Usuario {

    @Column(name = "obra_social")
    private String obraSocial;

    /** Hasta 40 caracteres; en la base va cifrado con AES-GCM (ADR-0022), por eso la columna es más ancha. */
    @Convert(converter = CifradoConverter.class)
    @Column(name = "nro_afiliado", length = 256)
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
