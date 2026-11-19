package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.CredencialesApi;
import ar.edu.mediconecta.api.dto.TokenApi;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import ar.edu.mediconecta.seguridad.ControlDeIngresos;
import ar.edu.mediconecta.seguridad.IdentidadJwt;
import ar.edu.mediconecta.seguridad.ServicioDeTokens;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.time.Duration;
import java.util.Optional;

/**
 * {@code POST /api/v1/auth/login}: cambia email y contraseña por un JWT. Es el único punto de la API donde
 * viaja la contraseña; el resto de los pedidos llevan {@code Authorization: Bearer <token>}.
 *
 * <p>Valida contra el mismo identity store que la web (PBKDF2 sobre la tabla usuario). La respuesta ante
 * credenciales inválidas es siempre la misma, exista o no el email, para no revelar qué cuentas existen. Cinco
 * fallos seguidos con un mismo email (exista o no la cuenta) dan <b>429</b> con {@code Retry-After} durante 15
 * minutos, y bloquean la cuenta si existe ({@link ControlDeIngresos}).
 *
 * <p>Errores en Problem Details: 400 si el cuerpo no es JSON, 422 si faltan el email o la contraseña, 401 con
 * credenciales inválidas, 403 con contraseña temporal y 429 tras demasiados intentos.
 *
 * <p>{@code POST /api/v1/auth/logout} cierra la sesión de la API: revoca el token con el que se lo llama (ADR-0019),
 * que desde ese momento recibe 401 aunque no haya vencido.
 */
@Path("/v1/auth")
@Stateless
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    private ControlDeIngresos controlDeIngresos;

    @Inject
    private ServicioDeUsuarios usuarios;

    @Inject
    private ServicioDeTokens tokens;

    @POST
    @Path("/login")
    public Response login(CredencialesApi credenciales, @Context UriInfo uri) {
        String instancia = uri == null ? null : uri.getRequestUri().getPath();
        if (credenciales == null) {
            return problema(TipoDeProblema.PEDIDO_INVALIDO, "Enviá un JSON con email y password.", instancia);
        }
        if (esVacio(credenciales.getEmail()) || esVacio(credenciales.getPassword())) {
            return problema(TipoDeProblema.DATOS_INVALIDOS, "Faltan el email o la contraseña.", instancia);
        }
        String email = credenciales.getEmail().trim();
        Optional<Duration> espera = controlDeIngresos.esperaPorIntentos(email);
        if (espera.isPresent()) {
            return Problemas.demasiadosIntentos(espera.get(), instancia).build();
        }
        CredentialValidationResult resultado = controlDeIngresos.validar( // con bloqueo por intentos fallidos (SEC-W01)
                new UsernamePasswordCredential(email, credenciales.getPassword()));
        Optional<Usuario> usuario = resultado.getStatus() == CredentialValidationResult.Status.VALID
                ? usuarios.buscarPorEmail(email) : Optional.empty();
        if (usuario.isEmpty()) {
            // El fallo que llega al máximo ya responde 429: así el que prueba contraseñas se entera recién ahí.
            return controlDeIngresos.esperaPorIntentos(email)
                    .map(restante -> Problemas.demasiadosIntentos(restante, instancia).build())
                    .orElseGet(() -> problema(TipoDeProblema.CREDENCIALES_INVALIDAS, "Email o contraseña incorrectos.", instancia));
        }
        if (usuario.get().isDebeCambiarPassword()) {
            // Una contraseña temporal no sirve para la API: primero hay que cambiarla desde "Mi perfil".
            return problema(TipoDeProblema.PASSWORD_TEMPORAL,
                    "Cambiá tu contraseña temporal desde Mi perfil antes de usar la API.", instancia);
        }
        String rol = usuario.get().getRol().name();
        return Response.ok(new TokenApi(tokens.emitir(usuario.get().getEmail(), rol), "Bearer",
                tokens.vigenciaEnSegundos(), rol)).build();
    }

    /** Revoca el token del pedido: 204 sin cuerpo. Sin token, 401; con uno inválido o ya revocado, 401 (FiltroTokenInvalido). */
    @POST
    @Path("/logout")
    public Response logout(@HeaderParam(HttpHeaders.AUTHORIZATION) String autorizacion, @Context UriInfo uri) {
        String instancia = uri == null ? null : uri.getRequestUri().getPath();
        Optional<IdentidadJwt> identidad = autorizacion != null && autorizacion.regionMatches(true, 0, "Bearer ", 0, 7)
                ? tokens.validar(autorizacion.substring(7))
                : Optional.empty();
        if (identidad.isEmpty()) {
            return problema(TipoDeProblema.NO_AUTENTICADO, "Para cerrar la sesión mandá el token en Authorization: Bearer.",
                    instancia);
        }
        tokens.revocar(identidad.get());
        return Response.noContent().build();
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static Response problema(TipoDeProblema tipo, String detalle, String instancia) {
        return Problemas.respuesta(Problemas.de(tipo, detalle, instancia)).build();
    }
}
