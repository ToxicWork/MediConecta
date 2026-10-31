package ar.externos.pasarela;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Como cualquier pasarela moderna, exige una clave secreta de comercio en {@code Authorization: Bearer}.
 * Sólo protege las rutas de la pasarela; las de videoconsulta tienen su propio esquema de API key.
 */
@Provider
public class AutenticacionPasarela implements ContainerRequestFilter {

    static final String CLAVE_SECRETA = "sk_test_mediconecta_2026";

    @Override
    public void filter(ContainerRequestContext pedido) {
        if (!pedido.getUriInfo().getPath().startsWith("/pasarela")) {
            return;
        }
        String autorizacion = pedido.getHeaderString("Authorization");
        if (!("Bearer " + CLAVE_SECRETA).equals(autorizacion)) {
            pedido.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(Map.of("error", "clave_invalida", "mensaje", "Falta la clave secreta del comercio."))
                    .build());
        }
    }
}
