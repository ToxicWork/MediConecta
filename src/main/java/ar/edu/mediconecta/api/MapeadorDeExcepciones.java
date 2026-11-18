package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.ProblemaApi;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.TransicionInvalidaException;
import ar.edu.mediconecta.negocio.excepciones.AccesoDenegadoException;
import ar.edu.mediconecta.negocio.excepciones.ClaveDeIdempotenciaReutilizadaException;
import ar.edu.mediconecta.negocio.excepciones.ParametroInvalidoException;
import ar.edu.mediconecta.negocio.excepciones.PrecondicionFallidaException;
import ar.edu.mediconecta.negocio.excepciones.TurnoInexistenteException;
import jakarta.ejb.EJBAccessException;
import jakarta.ejb.EJBException;
import jakarta.json.bind.JsonbException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Traduce toda excepción de la API a un error en formato Problem Details (RFC 9457, {@code application/problem+json})
 * con el código HTTP correcto. Ninguna respuesta expone trazas, nombres de clases ni de tablas, ni el mensaje interno
 * del runtime (OWASP: configuración de seguridad).
 */
@Provider
public class MapeadorDeExcepciones implements ExceptionMapper<Exception> {

    static final String NECESITAS_AUTENTICARTE =
            "Necesitás autenticarte: pedí un token en POST /api/v1/auth/login y envialo como Bearer.";
    static final String TU_ROL_NO_ALCANZA = "Tu rol no tiene permiso para esta operación.";
    static final String CUERPO_INVALIDO = "El cuerpo del pedido no es un JSON válido.";
    static final String NO_EXISTE = "El recurso no existe.";

    private static final Logger LOG = Logger.getLogger(MapeadorDeExcepciones.class.getName());

    @Context
    private HttpServletRequest pedido;

    @Override
    public Response toResponse(Exception excepcion) {
        Throwable causa = excepcion instanceof EJBException ejb && !(excepcion instanceof EJBAccessException)
                && ejb.getCause() != null ? ejb.getCause() : excepcion;

        boolean autenticado = pedido != null && pedido.getUserPrincipal() != null;
        ProblemaApi problema = clasificar(causa, autenticado, pedido == null ? null : pedido.getRequestURI());
        if (problema.status() >= 500) {
            LOG.log(Level.SEVERE, "Error no controlado en la API REST", excepcion);
        }
        return Problemas.respuesta(problema).build();
    }

    /**
     * Decide el problema de cada error. No usa el runtime de JAX-RS salvo para leer el estado de una
     * {@code WebApplicationException}, así que se prueba con JUnit sin contenedor.
     *
     * <ul>
     *   <li>{@link TransicionInvalidaException} (patrón State: la operación no vale en el estado actual del
     *       turno): <b>409 Conflict</b>, {@code transicion_invalida}. El pedido está bien formado, pero choca con el
     *       estado del recurso.</li>
     *   <li>{@link TurnoInexistenteException} (no existe o es ajeno): <b>404</b>, igual que un id inexistente.</li>
     *   <li>{@link AccesoDenegadoException}: 403. Otras reglas de negocio: 409, {@code regla_de_negocio}.</li>
     *   <li>Sin permiso: 401 si no hay token, 403 si el rol no alcanza.</li>
     *   <li>Un cuerpo que no es JSON: 400. Los errores del propio JAX-RS (ruta, método, formato) conservan su código,
     *       con un detalle fijo.</li>
     * </ul>
     */
    static ProblemaApi clasificar(Throwable causa, boolean autenticado, String instancia) {
        if (causa instanceof EJBAccessException) {
            return autenticado
                    ? Problemas.de(TipoDeProblema.PROHIBIDO, TU_ROL_NO_ALCANZA, instancia)
                    : Problemas.de(TipoDeProblema.NO_AUTENTICADO, NECESITAS_AUTENTICARTE, instancia);
        }
        if (causa instanceof WebApplicationException web) {
            return deEstadoHttp(web.getResponse().getStatus(), instancia);
        }
        if (causa instanceof ProcessingException || causa instanceof JsonbException) {
            return Problemas.de(TipoDeProblema.PEDIDO_INVALIDO, CUERPO_INVALIDO, instancia);
        }
        if (causa instanceof TurnoInexistenteException) {
            return Problemas.de(TipoDeProblema.NO_ENCONTRADO, NO_EXISTE, instancia);
        }
        if (causa instanceof AccesoDenegadoException acceso) {
            return Problemas.de(TipoDeProblema.PROHIBIDO, acceso.getMessage(), instancia);
        }
        if (causa instanceof PrecondicionFallidaException precondicion) {
            return Problemas.de(TipoDeProblema.PRECONDICION_FALLIDA, precondicion.getMessage(), instancia);
        }
        if (causa instanceof ClaveDeIdempotenciaReutilizadaException reutilizada) {
            return Problemas.de(TipoDeProblema.CLAVE_REUTILIZADA, reutilizada.getMessage(), instancia);
        }
        if (causa instanceof ParametroInvalidoException parametro) {
            return Problemas.de(TipoDeProblema.PEDIDO_INVALIDO, parametro.getMessage(), instancia);
        }
        if (causa instanceof TransicionInvalidaException transicion) {
            return Problemas.de(TipoDeProblema.TRANSICION_INVALIDA, transicion.getMessage(), instancia);
        }
        if (causa instanceof ErrorDeNegocioException negocio) {
            return Problemas.de(TipoDeProblema.REGLA_DE_NEGOCIO, negocio.getMessage(), instancia);
        }
        return Problemas.de(TipoDeProblema.ERROR_INTERNO, "Ocurrió un error inesperado.", instancia);
    }

    /** Los errores que devuelve el propio JAX-RS: el detalle es siempre un texto fijo, nunca su mensaje interno. */
    static ProblemaApi deEstadoHttp(int estado, String instancia) {
        return switch (estado) {
            case 400 -> Problemas.de(TipoDeProblema.PEDIDO_INVALIDO, CUERPO_INVALIDO, instancia);
            case 401 -> Problemas.de(TipoDeProblema.NO_AUTENTICADO, NECESITAS_AUTENTICARTE, instancia);
            case 403 -> Problemas.de(TipoDeProblema.PROHIBIDO, TU_ROL_NO_ALCANZA, instancia);
            case 404 -> Problemas.de(TipoDeProblema.NO_ENCONTRADO, NO_EXISTE, instancia);
            case 405 -> Problemas.de(TipoDeProblema.METODO_NO_PERMITIDO,
                    "Ese método HTTP no está disponible para este recurso.", instancia);
            case 406 -> Problemas.de(TipoDeProblema.NO_ACEPTABLE, "La API responde sólo JSON.", instancia);
            case 415 -> Problemas.de(TipoDeProblema.TIPO_NO_SOPORTADO,
                    "Enviá el cuerpo como JSON, con Content-Type: application/json.", instancia);
            default -> estado >= 500
                    ? Problemas.de(TipoDeProblema.ERROR_INTERNO, estado, TipoDeProblema.ERROR_INTERNO.titulo(),
                            "Ocurrió un error inesperado.", instancia)
                    : Problemas.de(TipoDeProblema.ERROR_HTTP, estado, titulo(estado), "El pedido no se pudo atender.", instancia);
        };
    }

    private static String titulo(int estado) {
        Response.Status conocido = Response.Status.fromStatusCode(estado);
        return conocido == null ? TipoDeProblema.ERROR_HTTP.titulo() : conocido.getReasonPhrase();
    }
}
