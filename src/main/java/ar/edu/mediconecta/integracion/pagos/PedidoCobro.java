package ar.edu.mediconecta.integracion.pagos;

import java.math.BigDecimal;

/** Lo que MediConecta le pide cobrar a la pasarela. Los datos de tarjeta viajan y no se guardan. */
public record PedidoCobro(String referencia, String descripcion, BigDecimal importe, String moneda,
                          String numeroTarjeta, String titular, String vencimiento, String codigoSeguridad) {
}
