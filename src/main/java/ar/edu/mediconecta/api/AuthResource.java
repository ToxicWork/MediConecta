package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.CredencialesApi;
import ar.edu.mediconecta.api.dto.ErrorApi;
import ar.edu.mediconecta.api.dto.TokenApi;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import ar.edu.mediconecta.seguridad.ControlDeIngresos;
import ar.edu.mediconecta.seguridad.ServicioDeTokens;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.security.enterprise.identitystore.CredentialValidationResult;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

/**
 * {@code POST /api/v1/auth/login}: cambia email y contraseña por un JWT. Es el único punto de la API donde
 * viaja la contraseña; el resto de los pedidos llevan {@code Authorization: Bearer <token>}.
 *
 * <p>Valida contra el mismo identity store que la web (PBKDF2 sobre la tabla usuario). La respuesta ante
 * credenciales inválidas es siempre la misma, exista o no el email y esté o no bloqueada la cuenta, para no
 * revelar qué cuentas existen. Cinco fallos seguidos bloquean la cuenta 15 minutos ({@link ControlDeIngresos}).
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
    public Response login(CredencialesApi credenciales) {
        if (credenciales == null || esVacio(credenciales.getEmail()) || esVacio(credenciales.getPassword())) {
            return error(400, "pedido_invalido", "Enviá un JSON con email y password.");
        }
        String email = credenciales.getEmail().trim();
        CredentialValidationResult resultado = controlDeIngresos.validar( // con bloqueo por intentos fallidos (SEC-W01)
                new UsernamePasswordCredential(email, credenciales.getPassword()));
        Optional<Usuario> usuario = resultado.getStatus() == CredentialValidationResult.Status.VALID
                ? usuarios.buscarPorEmail(email) : Optional.empty();
        if (usuario.isEmpty()) {
            return error(401, "credenciales_invalidas", "Email o contraseña incorrectos.");
        }
        if (usuario.get().isDebeCambiarPassword()) {
            // Una contraseña temporal no sirve para la API: primero hay que cambiarla desde "Mi perfil".
            return error(403, "password_temporal", "Cambiá tu contraseña temporal desde Mi perfil antes de usar la API.");
        }
        String rol = usuario.get().getRol().name();
        return Response.ok(new TokenApi(tokens.emitir(usuario.get().getEmail(), rol), "Bearer",
                tokens.vigenciaEnSegundos(), rol)).build();
    }

    private static boolean esVacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static Response error(int estado, String codigo, String mensaje) {
        return Response.status(estado).type(MediaType.APPLICATION_JSON).entity(ErrorApi.de(estado, codigo, mensaje)).build();
    }
}
