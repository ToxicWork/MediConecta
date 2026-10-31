package ar.edu.mediconecta.integracion;

import ar.edu.mediconecta.dao.LlamadaIntegracionDAO;
import ar.edu.mediconecta.modelo.LlamadaIntegracion;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bitácora de llamadas a sistemas externos. {@code REQUIRES_NEW}: cada registro se confirma en su propia
 * transacción, independiente de la del flujo de negocio. Si la confirmación de un turno hace rollback, el
 * registro del cobro y de su reembolso compensatorio quedan igual, que es lo que se quiere auditar.
 */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class RegistroIntegraciones {

    public static final String OK = "OK";
    public static final String RECHAZO = "RECHAZO";
    public static final String ERROR = "ERROR";
    public static final String CIRCUITO_ABIERTO = "CIRCUITO_ABIERTO";
    public static final String CACHE = "CACHE";
    public static final String PENDIENTE = "PENDIENTE";
    public static final String COMPENSACION = "COMPENSACION";

    private static final Logger LOG = Logger.getLogger(RegistroIntegraciones.class.getName());

    @Inject
    private LlamadaIntegracionDAO dao;

    public void registrar(SistemaExterno sistema, String operacion, String referencia, String resultado,
                          String detalle, long latenciaMs) {
        LOG.log(OK.equals(resultado) ? Level.INFO : Level.WARNING, "[" + sistema.getNombre() + " " + sistema.getProtocolo()
                + "] " + operacion + " (" + referencia + ") -> " + resultado + " en " + latenciaMs + " ms: " + detalle);
        dao.guardar(new LlamadaIntegracion(sistema.name(), operacion, referencia, resultado, detalle, latenciaMs));
    }
}
