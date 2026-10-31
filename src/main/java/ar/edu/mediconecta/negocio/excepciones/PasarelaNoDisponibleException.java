package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/** La pasarela no respondió (o su circuito está abierto). Sin cobro no se confirma un turno con importe. */
public class PasarelaNoDisponibleException extends ErrorDeNegocioException {

    public PasarelaNoDisponibleException() {
        super("No pudimos comunicarnos con la pasarela de pagos. Tu turno sigue reservado: probá de nuevo en unos minutos.");
    }
}
