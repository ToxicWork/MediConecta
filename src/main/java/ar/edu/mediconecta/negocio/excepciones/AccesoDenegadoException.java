package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/** Seguridad programática: el rol alcanza, pero el recurso no es del usuario (prevención de IDOR). */
public class AccesoDenegadoException extends ErrorDeNegocioException {

    public AccesoDenegadoException(String mensaje) {
        super(mensaje);
    }
}
