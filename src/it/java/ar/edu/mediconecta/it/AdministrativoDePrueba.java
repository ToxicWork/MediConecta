package ar.edu.mediconecta.it;

import ar.edu.mediconecta.negocio.ServicioDeReintegros;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/** El administrativo reintenta un reintegro pendiente, como desde la pantalla Pagos. */
@Stateless
@PermitAll
@RunAs("ADMINISTRATIVO")
@DeclareRoles("ADMINISTRATIVO")
public class AdministrativoDePrueba {

    @Inject
    private ServicioDeReintegros reintegros;

    public String reintentarAhora(Long id) {
        return reintegros.reintentarAhora(id);
    }
}
