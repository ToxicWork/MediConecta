package ar.edu.mediconecta.modelo;

import java.math.BigDecimal;

/** Lo que el flujo de confirmación resolvió para el turno: cobertura, prestación e importes. */
public record DatosConfirmacion(
        EstadoCobertura cobertura,
        String obraSocial,
        String plan,
        String codigoPrestacion,
        BigDecimal importeTotal,
        BigDecimal importePaciente,
        BigDecimal importeObraSocial,
        EstadoAutorizacion autorizacion) {
}
