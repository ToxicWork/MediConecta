package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PACIENTE")
public class Paciente extends Usuario {

    @Column(name = "obra_social")
    private String obraSocial;

    protected Paciente() {
    }

    public Paciente(String nombreCompleto, String email, String password, String obraSocial) {
        super(nombreCompleto, email, password, Rol.PACIENTE);
        this.obraSocial = obraSocial;
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public void setObraSocial(String obraSocial) {
        this.obraSocial = obraSocial;
    }
}
