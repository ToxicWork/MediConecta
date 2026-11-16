package ar.edu.mediconecta.integracion.pagos;

import java.math.BigDecimal;

public record ResultadoCobro(boolean aprobado, String idPago, String estado, String motivoRechazo,
                             BigDecimal importe, String moneda, String marca, String ultimos4) {

    /** La pasarela respondió que no conoce ese pago (HTTP 404): no hay nada que reembolsar ahí. */
    public static final String INEXISTENTE = "INEXISTENTE";

    public boolean inexistente() {
        return INEXISTENTE.equals(estado);
    }

    public String resumen() {
        return estado + (idPago == null ? "" : " " + idPago) + (importe == null ? "" : " $" + importe)
                + (motivoRechazo == null ? "" : " (" + motivoRechazo + ")");
    }

    public String motivoLegible() {
        if (motivoRechazo == null) {
            return "La pasarela rechazó el pago.";
        }
        return switch (motivoRechazo) {
            case "fondos_insuficientes" -> "La tarjeta no tiene fondos suficientes.";
            case "tarjeta_vencida", "vencimiento_invalido" -> "La tarjeta está vencida o el vencimiento no es válido.";
            case "tarjeta_invalida" -> "El número de tarjeta no es válido.";
            default -> "La pasarela rechazó el pago (" + motivoRechazo + ").";
        };
    }
}
