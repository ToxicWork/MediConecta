package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/** La pasarela respondió, pero rechazó el pago: el turno sigue en espera y el paciente puede usar otra tarjeta. */
public class PagoRechazadoException extends ErrorDeNegocioException {

    public PagoRechazadoException(String motivo) {
        super("Pago rechazado: " + motivo);
    }
}
