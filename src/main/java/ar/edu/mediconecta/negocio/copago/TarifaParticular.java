package ar.edu.mediconecta.negocio.copago;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Sin obra social o sin cobertura: el paciente paga el precio de lista. También es la estrategia por defecto. */
@ApplicationScoped
public class TarifaParticular implements CalculoCopago {

    @Override
    public boolean aplica(ResultadoCobertura cobertura) {
        return cobertura.getEstado() == EstadoCobertura.PARTICULAR;
    }

    @Override
    public ImporteTurno calcular(BigDecimal precioLista, ResultadoCobertura cobertura) {
        return new ImporteTurno(precioLista, precioLista, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                "Atención particular: el paciente abona el total");
    }
}
