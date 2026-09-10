package ar.edu.mediconecta.seguridad;

import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Optional;
import java.util.Set;

/**
 * Mecanismo de autenticación "custom form": el login.xhtml propio llama a
 * SecurityContext.authenticate() con las credenciales, y acá se validan contra el
 * IdentityStore declarativo (ConfiguracionSeguridad) y se notifica al contenedor.
 *
 * En cada request con sesión ya autenticada se vuelve a notificar el login (con el
 * rol vigente en base) para que el SecurityContext de Undertow quede correctamente
 * asociado a ESE request — imprescindible para que {@code @RolesAllowed} y las
 * vistas JSF vean el principal/los roles.
 */
@ApplicationScoped
public class MecanismoAutenticacion implements HttpAuthenticationMechanism {

    @Inject
    private IdentityStoreHandler identityStoreHandler;

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
            HttpMessageContext httpMessageContext) throws AuthenticationException {

        if (httpMessageContext.isAuthenticationRequest()) {
            CredentialValidationResult result = identityStoreHandler.validate(
                    httpMessageContext.getAuthParameters().getCredential());
            if (result.getStatus() == CredentialValidationResult.Status.VALID) {
                httpMessageContext.getMessageInfo().getMap()
                        .put("jakarta.servlet.http.registerSession", Boolean.TRUE.toString());
                return httpMessageContext.notifyContainerAboutLogin(result);
            }
            return httpMessageContext.responseUnauthorized();
        }

        if (request.getUserPrincipal() != null) {
            Optional<Usuario> usuario = servicioDeUsuarios.buscarPorEmail(request.getUserPrincipal().getName());
            if (usuario.isPresent()) {
                return httpMessageContext.notifyContainerAboutLogin(
                        request.getUserPrincipal(), Set.of(usuario.get().getRol().name()));
            }
        }

        if (httpMessageContext.isProtected()) {
            return httpMessageContext.redirect(request.getContextPath() + "/login.xhtml");
        }

        return httpMessageContext.doNothing();
    }
}
