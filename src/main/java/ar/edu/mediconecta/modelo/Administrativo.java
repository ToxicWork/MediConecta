package ar.edu.mediconecta.modelo;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

/**
 * Personal administrativo del centro. No atiende ni se atiende: por ahora sólo consulta los cobros a
 * pacientes y los reclamos a obras sociales (Pagos). No agrega columnas a la tabla {@code usuario}.
 */
@Entity
@DiscriminatorValue("ADMINISTRATIVO")
public class Administrativo extends Usuario {

    protected Administrativo() {
    }

    public Administrativo(String nombreCompleto, String email, String password) {
        super(nombreCompleto, email, password, Rol.ADMINISTRATIVO);
    }
}
