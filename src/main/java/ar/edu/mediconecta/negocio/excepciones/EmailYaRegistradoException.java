package ar.edu.mediconecta.negocio.excepciones;

import jakarta.ejb.ApplicationException;

@ApplicationException(rollback = true)
public class EmailYaRegistradoException extends RuntimeException {

    public EmailYaRegistradoException(String email) {
        super("El email " + email + " ya está registrado.");
    }
}
