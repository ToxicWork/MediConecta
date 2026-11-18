package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/**
 * La precondición {@code If-Match} de la API no se cumple: el turno cambió desde que el cliente lo leyó (otro
 * usuario lo modificó, o llegó la autorización de la obra social). La operación no se hace; la API responde 412.
 */
public class PrecondicionFallidaException extends ErrorDeNegocioException {

    public PrecondicionFallidaException() {
        super("El turno cambió desde que lo leíste: volvé a pedirlo (GET) y usá su ETag nueva en If-Match.");
    }
}
