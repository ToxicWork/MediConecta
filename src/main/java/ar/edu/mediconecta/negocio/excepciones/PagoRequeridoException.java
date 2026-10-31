package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

import java.math.BigDecimal;

/** El turno tiene un importe a cargo del paciente y no llegaron datos de pago (por ejemplo, cambió la cobertura). */
public class PagoRequeridoException extends ErrorDeNegocioException {

    public PagoRequeridoException(BigDecimal importe) {
        super("Este turno tiene un importe a tu cargo de $ " + importe.toPlainString()
                + ". Revisá la cotización e ingresá los datos de pago.");
    }
}
