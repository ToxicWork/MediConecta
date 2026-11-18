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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Valida credenciales con bloqueo por intentos fallidos (prueba de seguridad SEC-W01). Es el único punto por el
 * que pasan los dos ingresos: el de la aplicación web ({@link MecanismoAutenticacion}) y el de la API
 * ({@code AuthResource}), así que la regla vale igual en ambos.
 *
 * <ul>
 *   <li>Cada fallo suma uno; al quinto seguido la cuenta se bloquea 15 minutos ({@link PoliticaDeBloqueo}). Un
 *       ingreso correcto pone el contador en cero. La regla está en {@link Usuario#registrarIntentoFallido}.
 *       Con la cuenta bloqueada se rechaza incluso la contraseña correcta.</li>
 *   <li>Los mismos fallos se cuentan también por email en {@link LimiteDeIntentos}, exista o no la cuenta: es lo que
 *       usan la API y la web para responder 429 ante la fuerza bruta ({@link #esperaPorIntentos}). Como un email que
 *       no existe se bloquea igual que uno que sí, quien prueba contraseñas no puede saber qué cuentas existen.</li>
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

    @Inject
    private LimiteDeIntentos limite;

    public CredentialValidationResult validar(Credential credencial) {
        if (!(credencial instanceof UsernamePasswordCredential usuarioYClave)) {
            return identityStoreHandler.validate(credencial);
        }
        String email = usuarioYClave.getCaller();
        if (limite.espera(email).isPresent()) {
            return CredentialValidationResult.INVALID_RESULT; // demasiados fallos con este email: ni se prueba la clave
        }
        LocalDateTime ahora = LocalDateTime.now();
        Optional<Usuario> usuario = usuarioDAO.buscarPorEmailParaActualizar(email);
        if (usuario.isPresent() && usuario.get().estaBloqueado(ahora)) {
            return CredentialValidationResult.INVALID_RESULT;
        }

        CredentialValidationResult resultado = identityStoreHandler.validate(credencial);
        if (resultado.getStatus() == CredentialValidationResult.Status.VALID) {
            limite.registrarIngreso(email);
        } else {
            limite.registrarFallo(email);
        }

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

    /** Cuánto falta para que este email pueda volver a intentar (la API y la web responden 429); vacío si puede ya. */
    public Optional<Duration> esperaPorIntentos(String email) {
        return limite.espera(email);
    }
}
