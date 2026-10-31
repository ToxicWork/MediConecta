package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/**
 * El sistema legado de la obra social no respondió al pedir una autorización. Marca la transacción para
 * rollback: el consumidor de la cola la atrapa, no confirma el mensaje y el broker lo reentrega más tarde.
 */
public class ObraSocialNoDisponibleException extends ErrorDeNegocioException {

    public ObraSocialNoDisponibleException(String detalle) {
        super("El sistema de la obra social no está disponible: " + detalle);
    }
}
