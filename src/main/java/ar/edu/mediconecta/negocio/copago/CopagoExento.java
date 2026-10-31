package ar.edu.mediconecta.negocio.copago;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.integracion.obrasocial.TipoCopago;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** La obra social cubre el 100 % (por ejemplo PAMI o IOMA): no hay cobro al paciente. */
@ApplicationScoped
public class CopagoExento implements CalculoCopago {

    @Override
    public boolean aplica(ResultadoCobertura cobertura) {
        return cobertura.isCubierta() && cobertura.getTipoCopago() == TipoCopago.EXENTO;
    }

    @Override
    public ImporteTurno calcular(BigDecimal precioLista, ResultadoCobertura cobertura) {
        return new ImporteTurno(precioLista, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), precioLista,
                "Exento de copago: la obra social cubre el 100 %");
    }
}
