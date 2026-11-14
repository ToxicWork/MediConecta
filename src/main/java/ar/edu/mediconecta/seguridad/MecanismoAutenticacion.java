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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
 *   <li>API REST ({@code /api/*}): JWT, sin sesión. El cliente obtiene el token en
 *       {@code POST /api/v1/auth/login} y lo envía en {@code Authorization: Bearer}; si es inválido o venció
 *       se responde 401 con {@code WWW-Authenticate}. Sin token, la llamada sigue como anónima y los EJB
 *       deciden con {@code @RolesAllowed}/{@code @PermitAll}.</li>
 * </ul>
 */
@ApplicationScoped
public class MecanismoAutenticacion implements HttpAuthenticationMechanism {

    public static final String DESAFIO_BEARER = "Bearer realm=\"MediConecta API\"";
    public static final String TOKEN_INVALIDO = "mediconecta.tokenInvalido";
    private static final String ESQUEMA_BEARER = "Bearer ";

    @Inject
    private ControlDeIngresos controlDeIngresos;

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Inject
    private ServicioDeTokens tokens;

    @Override
    public AuthenticationStatus validateRequest(HttpServletRequest request, HttpServletResponse response,
            HttpMessageContext httpMessageContext) throws AuthenticationException {

        if (esApi(request)) {
            return autenticarApi(request, response, httpMessageContext);
        }

        if (httpMessageContext.isAuthenticationRequest()) {
            // Con el control de intentos fallidos (SEC-W01): la misma regla que el login de la API.
            CredentialValidationResult result = controlDeIngresos.validar(
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

    /**
     * API REST: sin sesión ni cookies. Cada pedido trae su JWT en {@code Authorization: Bearer}; la identidad
     * y el rol salen del token firmado, sin ir a la base. Sin cabecera, la llamada sigue como anónima y los
     * EJB deciden con {@code @RolesAllowed}/{@code @PermitAll}; con un token inválido o vencido, 401
     * (lo responde {@code FiltroTokenInvalido}), también en los recursos públicos.
     */
    private AuthenticationStatus autenticarApi(HttpServletRequest request, HttpServletResponse response,
                                               HttpMessageContext httpMessageContext) {
        String cabecera = request.getHeader("Authorization");
        if (cabecera == null) {
            return httpMessageContext.doNothing();
        }
        if (cabecera.regionMatches(true, 0, ESQUEMA_BEARER, 0, ESQUEMA_BEARER.length())) {
            Optional<IdentidadJwt> identidad = tokens.validar(cabecera.substring(ESQUEMA_BEARER.length()));
            if (identidad.isPresent()) {
                return httpMessageContext.notifyContainerAboutLogin(
                        identidad.get().email(), Set.of(identidad.get().rol()));
            }
        }
        // El 401 no se responde acá: el contenedor lo devolvería con su página HTML. Se marca el pedido y lo
        // rechaza FiltroTokenInvalido (JAX-RS) con el mismo JSON de error que usa el resto de la API.
        request.setAttribute(TOKEN_INVALIDO, Boolean.TRUE);
        return httpMessageContext.doNothing();
    }

    private static boolean esApi(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    private static boolean esRecursoEstatico(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/jakarta.faces.resource/");
    }
}
