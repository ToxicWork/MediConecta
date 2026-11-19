package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.PagoDAO;
import ar.edu.mediconecta.dao.ReintegroPendienteDAO;
import ar.edu.mediconecta.integracion.pagos.PagosGateway;
import ar.edu.mediconecta.integracion.pagos.ResultadoCobro;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.OrigenReintegro;
import ar.edu.mediconecta.modelo.ReintegroPendiente;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * D-06 y D-41: los reintegros que la pasarela no pudo hacer en el momento ya no quedan sólo en el log.
 *
 * <p>Registrar es {@code REQUIRES_NEW}: la compensación lo llama después del rollback de la confirmación, y el
 * reintegro tiene que quedar guardado aunque aquella transacción no exista más. Las operaciones del sistema (registrar
 * y reintentar) son {@code @PermitAll} porque las llaman la compensación, Facturación y el temporizador, sin un
 * usuario de por medio; las del administrativo exigen su rol.
 */
@Stateless
@DeclareRoles("ADMINISTRATIVO")
public class ServicioDeReintegrosBean implements ServicioDeReintegros {

    private static final Logger LOG = Logger.getLogger(ServicioDeReintegrosBean.class.getName());
    private static final String REEMBOLSADO = "REEMBOLSADO";

    @Inject
    private ReintegroPendienteDAO reintegros;

    @Inject
    private PagoDAO pagoDAO;

    @Inject
    private PagosGateway pasarela;

    @Resource
    private SessionContext contexto;

    private Clock reloj = Clock.systemDefaultZone();

    @Override
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void registrarParaReintentar(String idPago, String referencia, Long turnoId, BigDecimal importe, String motivo,
                                        OrigenReintegro origen, String error) {
        if (reintegros.existeAbiertoDePago(idPago)) {
            return;
        }
        reintegros.guardar(ReintegroPendiente.paraReintentar(idPago, referencia, turnoId, importe, motivo, origen, error, ahora()));
        LOG.warning("Reintegro del pago " + idPago + " (" + referencia + ") pendiente: la pasarela no respondió; se reintenta solo.");
    }

    @Override
    @PermitAll
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void registrarParaConciliar(String idPago, String referencia, Long turnoId, BigDecimal importe, String motivo,
                                       OrigenReintegro origen, String detalle) {
        if (reintegros.existeAbiertoDePago(idPago)) {
            return;
        }
        reintegros.guardar(ReintegroPendiente.paraConciliar(idPago, referencia, turnoId, importe, motivo, origen, detalle, ahora()));
        LOG.warning("Reintegro del pago " + idPago + " (" + referencia + ") para conciliación manual: " + detalle);
    }

    @Override
    @PermitAll
    public int reintentarVencidos() {
        List<ReintegroPendiente> vencidos = reintegros.buscarVencidos(ahora());
        for (ReintegroPendiente vencido : vencidos) {
            intentar(reintegros.buscarParaActualizar(vencido.getId()));
        }
        return vencidos.size();
    }

    @Override
    @RolesAllowed("ADMINISTRATIVO")
    public String reintentarAhora(Long id) {
        ReintegroPendiente reintegro = abierto(id);
        intentar(reintegro);
        return switch (reintegro.getEstado()) {
            case REINTEGRADO -> "Listo: la pasarela hizo el reintegro.";
            case PENDIENTE -> "La pasarela sigue sin responder; se vuelve a intentar solo.";
            default -> "La pasarela respondió que no puede hacerlo: " + reintegro.getUltimoError();
        };
    }

    @Override
    @RolesAllowed("ADMINISTRATIVO")
    public void marcarConciliado(Long id) {
        abierto(id).marcarConciliado(contexto.getCallerPrincipal().getName(), ahora());
    }

    @Override
    @RolesAllowed("ADMINISTRATIVO")
    public List<ReintegroPendiente> abiertos() {
        return reintegros.buscarAbiertos();
    }

    private ReintegroPendiente abierto(Long id) {
        ReintegroPendiente reintegro = id == null ? null : reintegros.buscarParaActualizar(id);
        if (reintegro == null || !reintegro.estaAbierto()) {
            throw new ErrorDeNegocioException("Ese reintegro ya no está pendiente.");
        }
        return reintegro;
    }

    /** Un intento: la pasarela es idempotente, así que reintentar un reintegro que ya se hizo no devuelve dos veces. */
    private void intentar(ReintegroPendiente reintegro) {
        if (reintegro == null || !reintegro.estaAbierto()) {
            return;
        }
        ResultadoCobro resultado;
        try {
            resultado = reintegro.getOrigen() == OrigenReintegro.COMPENSACION
                    ? pasarela.reembolsarCompensacion(reintegro.getIdPago(), reintegro.getMotivo(), reintegro.getReferencia())
                    : pasarela.reembolsar(reintegro.getIdPago(), reintegro.getMotivo(), reintegro.getReferencia());
        } catch (IntegracionNoDisponibleException sinRespuesta) {
            reintegro.registrarFalla(sinRespuesta.getMessage(), ahora());
            return;
        }
        if (REEMBOLSADO.equals(resultado.estado())) {
            reintegro.marcarReintegrado(ahora());
            if (reintegro.getTurnoId() != null) {
                pagoDAO.buscarAprobadoDeTurno(reintegro.getTurnoId())
                        .filter(pago -> reintegro.getIdPago().equals(pago.getIdPasarela()))
                        .ifPresent(pago -> pago.marcarReembolsado(reintegro.getMotivo()));
            }
            LOG.info("Reintegro del pago " + reintegro.getIdPago() + " hecho en el intento " + reintegro.getIntentos() + ".");
        } else {
            reintegro.pasarAConciliacionManual(resultado.inexistente()
                    ? "La pasarela no conoce el pago" : "La pasarela lo rechazó: " + resultado.resumen(), ahora());
        }
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(reloj);
    }
}
