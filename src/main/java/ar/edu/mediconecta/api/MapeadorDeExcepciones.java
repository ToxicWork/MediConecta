package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.ErrorApi;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.TransicionInvalidaException;
import ar.edu.mediconecta.seguridad.MecanismoAutenticacion;
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

        boolean autenticado = pedido != null && pedido.getUserPrincipal() != null;
        ErrorApi error = clasificar(causa, autenticado);
        Response.ResponseBuilder constructor = Response.status(error.estado());
        if (error.estado() == 401) {
            constructor.header("WWW-Authenticate", MecanismoAutenticacion.DESAFIO_BEARER);
        }
        if (error.estado() == 500) {
            LOG.log(Level.SEVERE, "Error no controlado en la API REST", excepcion);
        }
        return respuesta(constructor, error);
    }

    /**
     * Decide el código HTTP de cada error. No usa el runtime de JAX-RS salvo para leer el estado de una
     * {@code WebApplicationException}, así que se prueba con JUnit sin contenedor.
     *
     * <ul>
     *   <li>{@link TransicionInvalidaException} (patrón State: la operación no vale en el estado actual del
     *       turno): <b>409 Conflict</b>, código {@code transicion_invalida}. El pedido está bien formado, pero
     *       choca con el estado del recurso.</li>
     *   <li>Otras reglas de negocio: 409, código {@code regla_de_negocio}.</li>
     *   <li>Sin permiso: 401 si no hay token, 403 si el rol no alcanza.</li>
     * </ul>
     */
    static ErrorApi clasificar(Throwable causa, boolean autenticado) {
        if (causa instanceof EJBAccessException) {
            return autenticado
                    ? ErrorApi.de(403, "prohibido", "Tu rol no tiene permiso para esta operación.")
                    : ErrorApi.de(401, "no_autenticado",
                            "Necesitás autenticarte: pedí un token en POST /api/v1/auth/login y envialo como Bearer.");
        }
        if (causa instanceof WebApplicationException web) {
            int estado = web.getResponse().getStatus();
            return ErrorApi.de(estado, estado == 404 ? "no_encontrado" : "error_http",
                    estado == 404 ? "El recurso no existe." : web.getMessage());
        }
        if (causa instanceof TransicionInvalidaException transicion) {
            return ErrorApi.de(409, "transicion_invalida", transicion.getMessage());
        }
        if (causa instanceof ErrorDeNegocioException negocio) {
            return ErrorApi.de(409, "regla_de_negocio", negocio.getMessage());
        }
        return ErrorApi.de(500, "error_interno", "Ocurrió un error inesperado.");
    }

    private static Response respuesta(Response.ResponseBuilder constructor, ErrorApi cuerpo) {
        return constructor.type(MediaType.APPLICATION_JSON).entity(cuerpo).build();
    }
}
