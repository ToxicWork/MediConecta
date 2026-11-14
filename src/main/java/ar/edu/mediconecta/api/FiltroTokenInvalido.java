package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.ErrorApi;
import ar.edu.mediconecta.seguridad.MecanismoAutenticacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Corta con 401 todo pedido a la API que trajo una cabecera {@code Authorization} que no es un JWT válido
 * (firma alterada, vencido, otro esquema). La validación la hace {@code MecanismoAutenticacion}, que marca el
 * pedido; este filtro responde con el JSON de error uniforme y el desafío {@code WWW-Authenticate: Bearer}
 * (RFC 6750). Un pedido sin cabecera no pasa por acá: sigue como anónimo.
 */
@Provider
@PreMatching
public class FiltroTokenInvalido implements ContainerRequestFilter {

    @Context
    private HttpServletRequest pedido;

    @Override
    public void filter(ContainerRequestContext contexto) {
        if (pedido != null && pedido.getAttribute(MecanismoAutenticacion.TOKEN_INVALIDO) != null) {
            contexto.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .header("WWW-Authenticate", MecanismoAutenticacion.DESAFIO_BEARER + ", error=\"invalid_token\"")
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErrorApi.de(401, "token_invalido",
                            "El token es inválido o venció: pedí uno nuevo en POST /api/v1/auth/login."))
                    .build());
        }
    }
}
