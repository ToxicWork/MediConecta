package ar.edu.mediconecta.seguridad;

import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.PoliticaDeBloqueo;
import ar.edu.mediconecta.modelo.Usuario;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.Credential;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Valida credenciales con bloqueo por intentos fallidos (prueba de seguridad SEC-W01). Es el único punto por el
 * que pasan los dos ingresos: el de la aplicación web ({@link MecanismoAutenticacion}) y el de la API
 * ({@code AuthResource}), así que la regla vale igual en ambos.
 *
 * <ul>
 *   <li>Con la cuenta bloqueada se rechaza incluso la contraseña correcta, y la respuesta es la misma que ante
 *       una contraseña incorrecta: quien prueba contraseñas no puede distinguir una cuenta bloqueada de un
 *       error, ni una cuenta que existe de una que no.</li>
 *   <li>Cada fallo suma uno; al quinto seguido la cuenta se bloquea 15 minutos ({@link PoliticaDeBloqueo}). Un
 *       ingreso correcto pone el contador en cero. La regla está en {@link Usuario#registrarIntentoFallido}.</li>
 *   <li>Todo ocurre en una transacción (REQUIRED) con la fila del usuario bloqueada: dos intentos simultáneos
 *       no pueden perder un fallo.</li>
 * </ul>
 */
@Stateless
@PermitAll
public class ControlDeIngresos {

    private static final Logger LOG = Logger.getLogger(ControlDeIngresos.class.getName());

    @Inject
    private IdentityStoreHandler identityStoreHandler;

    @Inject
    private UsuarioDAO usuarioDAO;

    public CredentialValidationResult validar(Credential credencial) {
        if (!(credencial instanceof UsernamePasswordCredential usuarioYClave)) {
            return identityStoreHandler.validate(credencial);
        }
        LocalDateTime ahora = LocalDateTime.now();
        Optional<Usuario> usuario = usuarioDAO.buscarPorEmailParaActualizar(usuarioYClave.getCaller());
        if (usuario.isPresent() && usuario.get().estaBloqueado(ahora)) {
            return CredentialValidationResult.INVALID_RESULT;
        }

        CredentialValidationResult resultado = identityStoreHandler.validate(credencial);

        if (usuario.isPresent()) {
            Usuario cuenta = usuario.get();
            if (resultado.getStatus() == CredentialValidationResult.Status.VALID) {
                if (cuenta.getIntentosFallidos() > 0) {
                    cuenta.registrarIngresoExitoso();
                }
            } else if (cuenta.registrarIntentoFallido(ahora)) {
                // Sin el email (SEC-D02): el id alcanza para encontrar la cuenta.
                LOG.warning("Cuenta del usuario " + cuenta.getId() + " bloqueada "
                        + PoliticaDeBloqueo.DURACION_BLOQUEO.toMinutes() + " minutos tras "
                        + PoliticaDeBloqueo.MAX_INTENTOS_FALLIDOS + " intentos de ingreso fallidos.");
            }
        }
        return resultado;
    }
}
