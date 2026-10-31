package ar.edu.mediconecta.negocio.copago;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.integracion.obrasocial.TipoCopago;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** El afiliado paga un porcentaje del precio (por ejemplo OSDE: 30 %). */
@ApplicationScoped
public class CopagoPorcentual implements CalculoCopago {

    private static final BigDecimal CIEN = new BigDecimal("100");

    @Override
    public boolean aplica(ResultadoCobertura cobertura) {
        return cobertura.isCubierta() && cobertura.getTipoCopago() == TipoCopago.PORCENTAJE;
    }

    @Override
    public ImporteTurno calcular(BigDecimal precioLista, ResultadoCobertura cobertura) {
        BigDecimal porcentaje = cobertura.getValorCopago() == null ? BigDecimal.ZERO : cobertura.getValorCopago();
        BigDecimal paciente = precioLista.multiply(porcentaje).divide(CIEN, 2, RoundingMode.HALF_UP);
        return new ImporteTurno(precioLista, paciente, precioLista.subtract(paciente),
                "Copago del " + porcentaje.stripTrailingZeros().toPlainString() + " % a cargo del afiliado");
    }
}
