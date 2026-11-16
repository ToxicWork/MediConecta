package ar.edu.mediconecta.negocio;

import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * Puerta de entrada a la falla de demostración del flujo crítico ({@link InyectorDeFallas}). Existe para que
 * armarla sea una operación protegida por rol como cualquier otra (seguridad declarativa): sólo el
 * administrativo puede hacer fallar la próxima confirmación, y lo hace desde su pantalla de Pagos.
 *
 * <p>No toca la base ni el broker, por eso no abre transacción.
 */
@Stateless
@DeclareRoles({"ADMINISTRATIVO"})
@RolesAllowed("ADMINISTRATIVO")
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class ControlDeDemostracion {

    @Inject
    private InyectorDeFallas inyector;

    /** La próxima confirmación de turno falla después de cobrar, encolar y publicar, justo antes del commit. */
    public void armarFalla() {
        inyector.armar();
    }

    public void desarmarFalla() {
        inyector.desarmar();
    }

    public boolean isFallaArmada() {
        return inyector.isArmada();
    }
}
