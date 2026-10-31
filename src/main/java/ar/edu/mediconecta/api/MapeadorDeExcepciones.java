package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.ErrorApi;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Traduce toda excepción de la API a un JSON uniforme ({@link ErrorApi}) con el código HTTP correcto.
 * Ninguna respuesta expone trazas, nombres de clases ni de tablas (OWASP: configuración de seguridad).
 */
@Provider
public class MapeadorDeExcepciones implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(MapeadorDeExcepciones.class.getName());

    @Context
    private HttpServletRequest pedido;

    @Override
    public Response toResponse(Exception excepcion) {
        Throwable causa = excepcion instanceof EJBException ejb && !(excepcion instanceof EJBAccessException)
                && ejb.getCause() != null ? ejb.getCause() : excepcion;

        if (causa instanceof EJBAccessException) {
            if (pedido == null || pedido.getUserPrincipal() == null) {
                return respuesta(Response.status(Response.Status.UNAUTHORIZED)
                        .header("WWW-Authenticate", "Basic realm=\"MediConecta API\""),
                        ErrorApi.de(401, "no_autenticado", "Necesitás autenticarte (HTTP Basic con tu email y contraseña)."));
            }
            return respuesta(Response.status(Response.Status.FORBIDDEN),
                    ErrorApi.de(403, "prohibido", "Tu rol no tiene permiso para esta operación."));
        }
        if (causa instanceof WebApplicationException web) {
            int estado = web.getResponse().getStatus();
            return respuesta(Response.status(estado), ErrorApi.de(estado,
                    estado == 404 ? "no_encontrado" : "error_http", estado == 404 ? "El recurso no existe." : web.getMessage()));
        }
        if (causa instanceof ErrorDeNegocioException negocio) {
            return respuesta(Response.status(Response.Status.CONFLICT), ErrorApi.de(409, "regla_de_negocio", negocio.getMessage()));
        }
        LOG.log(Level.SEVERE, "Error no controlado en la API REST", excepcion);
        return respuesta(Response.serverError(), ErrorApi.de(500, "error_interno", "Ocurrió un error inesperado."));
    }

    private static Response respuesta(Response.ResponseBuilder constructor, ErrorApi cuerpo) {
        return constructor.type(MediaType.APPLICATION_JSON).entity(cuerpo).build();
    }
}
