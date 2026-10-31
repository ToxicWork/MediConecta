package ar.edu.mediconecta.integracion.pagos;

import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;

/** Gateway hacia la pasarela de pagos (partner moderno, REST). */
public interface PagosGateway {

    /** Un pago rechazado es una respuesta válida ({@code aprobado=false}); la indisponibilidad lanza excepción. */
    ResultadoCobro cobrar(PedidoCobro pedido);

    /** Reembolso total. La pasarela lo hace idempotente: reembolsar dos veces no devuelve dos veces. */
    ResultadoCobro reembolsar(String idPago, String motivo, String referencia);

    /** Reembolso disparado por un rollback (transacción compensatoria); se registra como COMPENSACION. */
    ResultadoCobro reembolsarCompensacion(String idPago, String motivo, String referencia);

    EstadoCircuito estadoCircuito();
}
