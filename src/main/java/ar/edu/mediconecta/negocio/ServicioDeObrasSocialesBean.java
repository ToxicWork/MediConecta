package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.integracion.obrasocial.CoberturaGateway;
import ar.edu.mediconecta.integracion.obrasocial.PedidoAutorizacion;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoAutorizacion;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.mensajeria.MensajeAutorizacion;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Prestacion;
import ar.edu.mediconecta.negocio.excepciones.ObraSocialNoDisponibleException;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.time.LocalDateTime;

/**
 * Componente de integración con la obra social (sistema legado, SOAP). Stateless: cada consulta es
 * independiente. La validación es {@code SUPPORTS}: se une a la transacción del que llama si la hay (la
 * confirmación) y no abre una propia para una consulta remota sin efectos (la cotización).
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL", "SISTEMA"})
public class ServicioDeObrasSocialesBean implements ServicioDeObrasSociales {

    @Inject
    private CoberturaGateway gateway;

    @Override
    @RolesAllowed("PACIENTE")
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public ResultadoCobertura validarCobertura(Paciente paciente, Prestacion prestacion, LocalDateTime fecha,
                                               String referencia) {
        if (!paciente.tieneObraSocial()) {
            return ResultadoCobertura.sinObraSocial();
        }
        return gateway.validarCobertura(paciente, prestacion, fecha, referencia);
    }

    /** Lo invoca el consumidor de la cola, que corre con la identidad técnica SISTEMA ({@code @RunAs}). */
    @Override
    @RolesAllowed("SISTEMA")
    public ResultadoAutorizacion autorizar(MensajeAutorizacion mensaje) {
        try {
            return gateway.autorizar(new PedidoAutorizacion(mensaje.idSolicitud(), mensaje.obraSocial(), mensaje.plan(),
                    mensaje.nroAfiliado(), mensaje.codigoPrestacion(), mensaje.fechaPrestacion(),
                    mensaje.matriculaPrestador()));
        } catch (IntegracionNoDisponibleException falla) {
            throw new ObraSocialNoDisponibleException(falla.getMessage());
        }
    }

    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public EstadoCircuito estadoConexion() {
        return gateway.estadoCircuito();
    }
}
