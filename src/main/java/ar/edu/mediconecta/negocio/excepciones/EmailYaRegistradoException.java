package ar.edu.mediconecta.negocio.excepciones;

public class EmailYaRegistradoException extends RuntimeException {

    public EmailYaRegistradoException(String email) {
        super("El email " + email + " ya está registrado.");
    }
}
