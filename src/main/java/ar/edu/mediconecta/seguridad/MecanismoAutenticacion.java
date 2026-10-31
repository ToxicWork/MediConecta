package ar.edu.mediconecta.seguridad;

import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.security.enterprise.AuthenticationException;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.authentication.mechanism.http.HttpAuthenticationMechanism;
import jakarta.security.enterprise.authentication.mechanism.http.HttpMessageContext;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.security.enterprise.identitystore.IdentityStoreHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * Mecanismo de autenticación propio con dos caminos sobre el mismo IdentityStore declarativo
 * ({@link ConfiguracionSeguridad}, PBKDF2 contra la tabla usuario):
 *
 * <ul>
 *   <li>Aplicación web (JSF): "custom form". login.xhtml llama a {@code SecurityContext.authenticate()} y se
 *       abre una sesión. En cada request ya autenticado se vuelve a notificar el login con el rol vigente para
 *       que {@code @RolesAllowed} y las vistas vean el principal.</li>
 *   <li>API REST ({@code /api/*}): HTTP Basic, sin sesión. Cada request trae sus credenciales; si son
 *       inválidas se responde 401 con {@code WWW-Authenticate}. Sin credenciales, la llamada sigue como
 *       anónima y los EJB deciden con {@code @RolesAllowed}/{@code @PermitAll}.</li>
 * </ul>
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

        if (esApi(request)) {
            return autenticarApi(request, response, httpMessageContext);
        }

        if (httpMessageContext.isAuthenticationRequest()) {
            CredentialValidationResult result = identityStoreHandler.validate(
                    httpMessageContext.getAuthParameters().getCredential());
            if (result.getStatus() == CredentialValidationResult.Status.VALID) {
                httpMessageContext.getMessageInfo().getMap()
                        .put("jakarta.servlet.http.registerSession", Boolean.TRUE.toString());
                return httpMessageContext.notifyContainerAboutLogin(result);
            }
            // SEND_FAILURE sin responseUnauthorized(): este último manda un 401 crudo y corta
            // la respuesta, y LoginBean no llega a mostrar "Credenciales inválidas".
            return AuthenticationStatus.SEND_FAILURE;
        }

        // Los recursos estáticos (CSS, JS, imágenes de PrimeFaces) no están protegidos ni usan
        // roles: no tiene sentido ir a la base por cada uno (son ~10 por página).
        if (esRecursoEstatico(request)) {
            return httpMessageContext.doNothing();
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

    private AuthenticationStatus autenticarApi(HttpServletRequest request, HttpServletResponse response,
                                               HttpMessageContext httpMessageContext) {
        String cabecera = request.getHeader("Authorization");
        if (cabecera == null || !cabecera.regionMatches(true, 0, "Basic ", 0, 6)) {
            return httpMessageContext.doNothing();
        }
        String[] partes;
        try {
            partes = new String(Base64.getDecoder().decode(cabecera.substring(6).trim()), StandardCharsets.UTF_8)
                    .split(":", 2);
        } catch (IllegalArgumentException mal) {
            partes = new String[0];
        }
        if (partes.length == 2) {
            CredentialValidationResult result = identityStoreHandler.validate(
                    new UsernamePasswordCredential(partes[0], partes[1]));
            if (result.getStatus() == CredentialValidationResult.Status.VALID) {
                return httpMessageContext.notifyContainerAboutLogin(result);
            }
        }
        response.setHeader("WWW-Authenticate", "Basic realm=\"MediConecta API\"");
        return httpMessageContext.responseUnauthorized();
    }

    private static boolean esApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    private static boolean esRecursoEstatico(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/jakarta.faces.resource/");
    }
}
