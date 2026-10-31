package ar.edu.mediconecta.negocio.copago;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * La obra social no respondió y no había caché: no se cobra nada ahora (RN-15). La cola de autorizaciones
 * resolverá la cobertura cuando el legado vuelva; si no hay cobertura, el paciente abona en el consultorio.
 */
@ApplicationScoped
public class CoberturaPendiente implements CalculoCopago {

    @Override
    public boolean aplica(ResultadoCobertura cobertura) {
        return cobertura.isPendiente();
    }

    @Override
    public ImporteTurno calcular(BigDecimal precioLista, ResultadoCobertura cobertura) {
        BigDecimal cero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new ImporteTurno(precioLista, cero, cero, "Cobertura pendiente: no se cobra hasta que responda la obra social");
    }
}
