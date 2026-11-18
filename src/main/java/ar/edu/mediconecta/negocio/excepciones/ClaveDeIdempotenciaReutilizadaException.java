package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/**
 * El cliente mandó una {@code Idempotency-Key} que ya había usado para otro pedido. La operación no se hace; la API
 * responde 422.
 */
public class ClaveDeIdempotenciaReutilizadaException extends ErrorDeNegocioException {

    public ClaveDeIdempotenciaReutilizadaException() {
        super("Esa Idempotency-Key ya se usó para otro pedido: generá una nueva para cada operación.");
    }
}
