package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("PROFESIONAL")
public class Profesional extends Usuario {

    @Column(name = "matricula")
    private String matricula;

    @Column(name = "especialidad")
    private String especialidad;

    protected Profesional() {
    }

    public Profesional(String nombreCompleto, String email, String password, String matricula, String especialidad) {
        super(nombreCompleto, email, password, Rol.PROFESIONAL);
        this.matricula = matricula;
        this.especialidad = especialidad;
    }

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }
}
