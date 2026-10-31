package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.SolicitudAutorizacionDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.MensajeAutorizacion;
import ar.edu.mediconecta.mensajeria.ProductorSolicitudesAutorizacion;
import ar.edu.mediconecta.mensajeria.PublicadorEventosTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Prestacion;
import ar.edu.mediconecta.modelo.SolicitudAutorizacion;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.catalogo.CatalogoPrestaciones;
import ar.edu.mediconecta.negocio.copago.ImporteTurno;
import ar.edu.mediconecta.negocio.copago.SelectorCalculoCopago;
import ar.edu.mediconecta.negocio.dto.CotizacionTurnoDTO;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import ar.edu.mediconecta.negocio.excepciones.PagoRequeridoException;
import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;
import ar.edu.mediconecta.negocio.validacion.CadenaDeValidacion;
import ar.edu.mediconecta.negocio.validacion.SolicitudReserva;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Flujo de negocio crítico de varios pasos: <b>reservar → validar cobertura → cobrar → confirmar</b>, con
 * transacción declarativa (CMT). {@link #confirmar} es {@code REQUIRED}: el contenedor abre una transacción
 * JTA al entrar y la confirma al salir; si cualquier paso lanza una excepción, la revierte entera.
 *
 * <table>
 *   <caption>Qué pasa con cada recurso si el flujo falla a mitad de camino</caption>
 *   <tr><th>Recurso</th><th>Ante un rollback</th></tr>
 *   <tr><td>Base de datos (turno, pago, solicitud, evento_publicado)</td><td>se revierte sola (JTA)</td></tr>
 *   <tr><td>Mensajes a cola.autorizaciones y topico.turnos</td><td>no salen nunca (Transactional Client, XA)</td></tr>
 *   <tr><td>Cobro en la pasarela (REST, no transaccional)</td><td>se compensa con un reembolso
 *       ({@link CompensacionDePago}, registrada como {@code Synchronization} de la transacción)</td></tr>
 *   <tr><td>Consulta de cobertura (SOAP, sin efectos)</td><td>no requiere compensación</td></tr>
 * </table>
 */
@Stateless
@DeclareRoles({"PACIENTE"})
@RolesAllowed("PACIENTE")
public class ProcesoConfirmacionTurno {

    private static final Logger LOG = Logger.getLogger(ProcesoConfirmacionTurno.class.getName());

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private CadenaDeValidacion validaciones;

    @Inject
    private CatalogoPrestaciones catalogo;

    @Inject
    private ServicioDeObrasSociales obrasSociales;

    @Inject
    private SelectorCalculoCopago copagos;

    @Inject
    private ServicioDePagos pagos;

    @Inject
    private SolicitudAutorizacionDAO solicitudes;

    @Inject
    private ProductorSolicitudesAutorizacion colaAutorizaciones;

    @Inject
    private PublicadorEventosTurno publicador;

    @Inject
    private InyectorDeFallas inyectorDeFallas;

    /** Sin transacción: es una consulta, y no tiene sentido retener una conexión mientras se espera al legado. */
    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public CotizacionTurnoDTO cotizar(Long turnoId) {
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null || turno.getPaciente() == null) {
            throw new ReservaInvalidaException("El turno ya no está retenido a tu nombre.");
        }
        Prestacion prestacion = catalogo.para(turno);
        ResultadoCobertura cobertura = obrasSociales.validarCobertura(turno.getPaciente(), prestacion,
                turno.getFechaHora(), "turno-" + turno.getId());
        ImporteTurno importe = copagos.para(cobertura).calcular(prestacion.getPrecio(), cobertura);
        return new CotizacionTurnoDTO(prestacion, cobertura, importe);
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno confirmar(Long turnoId, Long pacienteId, DatosPago datosPago) {
        Turno turno = turnoDAO.buscarPorId(turnoId);

        // 1. Reglas de la reserva (Chain of Responsibility)
        validaciones.paraConfirmar().validar(new SolicitudReserva(turno, pacienteId, LocalDateTime.now()));
        Paciente paciente = turno.getPaciente();

        // 2. Cobertura con la obra social: SOAP sincrónico, con Circuit Breaker y Fallback (nunca falla)
        Prestacion prestacion = catalogo.para(turno);
        ResultadoCobertura cobertura = obrasSociales.validarCobertura(paciente, prestacion, turno.getFechaHora(),
                "turno-" + turno.getId());

        // 3. Copago según la cobertura (Strategy)
        ImporteTurno importe = copagos.para(cobertura).calcular(prestacion.getPrecio(), cobertura);

        // 4. Cobro por REST a la pasarela, si hay importe a cargo del paciente (compensable)
        if (importe.isRequierePago()) {
            if (datosPago == null) {
                throw new PagoRequeridoException(importe.getImportePaciente());
            }
            pagos.cobrar(turno, importe.getImportePaciente(), datosPago);
        }

        // 5. Confirmación (State: HOLD -> CONFIRMADO)
        EstadoAutorizacion autorizacion = cobertura.isRequiereAutorizacion()
                ? EstadoAutorizacion.PENDIENTE : EstadoAutorizacion.NO_REQUIERE;
        turno.confirmar(new DatosConfirmacion(cobertura.getEstado(),
                paciente.tieneObraSocial() ? paciente.getObraSocial() : null, cobertura.getPlan(),
                prestacion.getCodigo(), importe.getPrecioLista(), importe.getImportePaciente(),
                importe.getImporteObraSocial(), autorizacion));

        // 6. Solicitud de autorización a la obra social por la cola punto a punto (asincrónica)
        if (autorizacion == EstadoAutorizacion.PENDIENTE) {
            SolicitudAutorizacion solicitud = new SolicitudAutorizacion(turno, paciente.getObraSocial(), prestacion.getCodigo());
            solicitudes.guardar(solicitud);
            colaAutorizaciones.enviar(MensajeAutorizacion.de(solicitud, turno, cobertura.getPlan()));
        }

        // 7. Evento de dominio al tópico (notificaciones, auditoría, facturación)
        publicador.publicar(EventoTurno.builder().tipo(TipoEventoTurno.TURNO_CONFIRMADO).turno(turno).build());

        // Demo: falla inyectada después de todos los pasos y antes del commit
        inyectorDeFallas.fallarSiEstaArmada();

        LOG.info("Turno " + turno.getId() + " confirmado: cobertura " + cobertura.getEstado() + ", a cargo del paciente $"
                + importe.getImportePaciente() + ", autorización " + autorizacion + ".");
        return turno;
    }
}
