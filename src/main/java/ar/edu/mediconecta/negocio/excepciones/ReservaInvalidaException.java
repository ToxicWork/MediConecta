package ar.edu.mediconecta.negocio.excepciones;

/** La lanza el primer eslabón de la cadena de validación de reserva que no se cumple. */
public class ReservaInvalidaException extends TurnoNoDisponibleException {

    public ReservaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
