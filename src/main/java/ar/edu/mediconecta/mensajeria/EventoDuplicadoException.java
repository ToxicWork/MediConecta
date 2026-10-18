package ar.edu.mediconecta.mensajeria;

public class EventoDuplicadoException extends RuntimeException {

    public EventoDuplicadoException(String idEvento, String suscriptor) {
        super("El evento " + idEvento + " ya fue procesado por " + suscriptor);
    }
}
