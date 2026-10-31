package ar.edu.mediconecta.modelo;

/** Una operación no admitida por el estado actual del turno (patrón State). Revierte la transacción. */
public class TransicionInvalidaException extends ErrorDeNegocioException {

    public TransicionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
