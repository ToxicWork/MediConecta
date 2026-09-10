package ar.edu.mediconecta.negocio.excepciones;

public class TurnoNoDisponibleException extends RuntimeException {

    public TurnoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
