package ar.edu.mediconecta.negocio.copago;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.integracion.obrasocial.TipoCopago;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** El afiliado paga un importe fijo, nunca mayor que el precio (por ejemplo Swiss Medical). */
@ApplicationScoped
public class CopagoMontoFijo implements CalculoCopago {

    @Override
    public boolean aplica(ResultadoCobertura cobertura) {
        return cobertura.isCubierta() && cobertura.getTipoCopago() == TipoCopago.MONTO_FIJO;
    }

    @Override
    public ImporteTurno calcular(BigDecimal precioLista, ResultadoCobertura cobertura) {
        BigDecimal fijo = cobertura.getValorCopago() == null ? BigDecimal.ZERO : cobertura.getValorCopago();
        BigDecimal paciente = fijo.min(precioLista).setScale(2, RoundingMode.HALF_UP);
        return new ImporteTurno(precioLista, paciente, precioLista.subtract(paciente), "Copago fijo de la obra social");
    }
}
