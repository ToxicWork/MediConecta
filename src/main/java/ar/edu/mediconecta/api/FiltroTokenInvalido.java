package ar.edu.mediconecta.api;

import ar.edu.mediconecta.seguridad.MecanismoAutenticacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

/**
 * Corta con 401 todo pedido a la API que trajo una cabecera {@code Authorization} que no es un JWT válido
 * (firma alterada, vencido, otro esquema). La validación la hace {@code MecanismoAutenticacion}, que marca el
 * pedido; este filtro responde con el error en Problem Details y el desafío {@code WWW-Authenticate: Bearer}
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
            contexto.abortWith(Problemas.respuesta(Problemas.de(TipoDeProblema.TOKEN_INVALIDO,
                    "El token es inválido o venció: pedí uno nuevo en POST /api/v1/auth/login.",
                    pedido.getRequestURI())).build());
        }
    }
}
