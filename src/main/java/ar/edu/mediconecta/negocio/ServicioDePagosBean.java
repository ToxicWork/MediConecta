package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.PagoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.integracion.pagos.PagosGateway;
import ar.edu.mediconecta.integracion.pagos.PedidoCobro;
import ar.edu.mediconecta.integracion.pagos.ResultadoCobro;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.modelo.OrigenReintegro;
import ar.edu.mediconecta.modelo.Pago;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import ar.edu.mediconecta.negocio.excepciones.DatosDePagoInvalidosException;
import ar.edu.mediconecta.negocio.excepciones.PagoRechazadoException;
import ar.edu.mediconecta.negocio.excepciones.PasarelaNoDisponibleException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

@Stateless
@DeclareRoles({"PACIENTE", "ADMINISTRATIVO", "SISTEMA"})
public class ServicioDePagosBean implements ServicioDePagos {

    private static final Logger LOG = Logger.getLogger(ServicioDePagosBean.class.getName());
    private static final String MONEDA = "ARS";

    @Inject
    private PagosGateway pasarela;

    @Inject
    private PagoDAO pagoDAO;

    @Inject
    private ServicioDeReintegros reintegros;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private Validator validador;

    @Resource
    private TransactionSynchronizationRegistry transaccion;

    @Resource
    private SessionContext contexto;

    /**
     * {@code MANDATORY}: un cobro sólo tiene sentido dentro de la transacción de la confirmación, porque de
     * ella depende la compensación. Llamarlo sin transacción es un error de programación y el contenedor lo
     * rechaza con {@code EJBTransactionRequiredException}.
     */
    @Override
    @RolesAllowed("PACIENTE")
    @TransactionAttribute(TransactionAttributeType.MANDATORY)
    public Pago cobrar(Turno turno, BigDecimal importe, DatosPago datosPago) {
        Set<ConstraintViolation<DatosPago>> violaciones = validador.validate(datosPago);
        if (!violaciones.isEmpty()) {
            // Mismo mensaje que muestra la pantalla (D-19); el de Bean Validation queda de respaldo.
            ConstraintViolation<DatosPago> primera = violaciones.iterator().next();
            throw new DatosDePagoInvalidosException(datosPago.mensajeDeError()
                    .orElse("Revisá los datos de la tarjeta: " + primera.getMessage() + "."));
        }
        ResultadoCobro resultado;
        try {
            resultado = pasarela.cobrar(new PedidoCobro("turno-" + turno.getId(),
                    "Turno " + turno.getModalidad().getEtiqueta() + " con " + turno.getProfesional().getNombreCompleto(),
                    importe, MONEDA, datosPago.getNumeroTarjeta(), datosPago.getTitular(), datosPago.getVencimiento(),
                    datosPago.getCodigoSeguridad()));
        } catch (IntegracionNoDisponibleException falla) {
            throw new PasarelaNoDisponibleException();
        }
        if (!resultado.aprobado()) {
            throw new PagoRechazadoException(resultado.motivoLegible());
        }
        Pago pago = new Pago(turno, importe, MONEDA, resultado.idPago(), resultado.marca(), resultado.ultimos4());
        pagoDAO.guardar(pago);
        // Desde acá el dinero ya se movió: si la transacción no llega al commit, hay que devolverlo.
        transaccion.registerInterposedSynchronization(new CompensacionDePago(pasarela, reintegros, resultado.idPago(),
                "turno-" + turno.getId(), turno.getId(), importe));
        LOG.info("Cobro aprobado " + resultado.idPago() + " por $" + importe + " (turno " + turno.getId() + ").");
        return pago;
    }

    /** Reembolso por cancelación. Lo invoca Facturación (identidad SISTEMA) después del commit de la cancelación. */
    @Override
    @RolesAllowed("SISTEMA")
    public void reembolsarPagoDeTurno(Long turnoId, String motivo) {
        Optional<Pago> pago = pagoDAO.buscarAprobadoDeTurno(turnoId);
        if (pago.isEmpty()) {
            return;
        }
        ResultadoCobro resultado;
        try {
            resultado = pasarela.reembolsar(pago.get().getIdPasarela(), motivo, "turno-" + turnoId);
        } catch (IntegracionNoDisponibleException falla) {
            throw new PasarelaNoDisponibleException();
        }
        if (resultado.inexistente()) {
            // La pasarela está en línea pero no conoce el pago: reintentar no lo arregla. El pago no se marca como
            // reembolsado y queda para conciliación manual, sin devolver el mensaje al broker ni abrir el circuito.
            // D-41: antes quedaba sólo en el log; ahora el administrativo lo ve en Pagos y lo resuelve.
            reintegros.registrarParaConciliar(pago.get().getIdPasarela(), "turno-" + turnoId, turnoId,
                    pago.get().getImporte(), motivo, OrigenReintegro.CANCELACION, "La pasarela no conoce el pago");
            return;
        }
        pago.get().marcarReembolsado(motivo);
    }

    /** Titularidad: el paciente sólo ve sus propios pagos (se resuelve con el principal autenticado). */
    @Override
    @RolesAllowed("PACIENTE")
    public List<Pago> pagosDelUsuarioActual() {
        Usuario usuario = usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName()).orElse(null);
        return usuario == null ? List.of() : pagoDAO.buscarDePaciente(usuario.getId());
    }

    @Override
    @RolesAllowed("ADMINISTRATIVO")
    public List<Pago> todosLosPagos() {
        return pagoDAO.buscarTodos();
    }
}
