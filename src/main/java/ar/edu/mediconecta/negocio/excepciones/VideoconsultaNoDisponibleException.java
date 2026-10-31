package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

public class VideoconsultaNoDisponibleException extends ErrorDeNegocioException {

    public VideoconsultaNoDisponibleException() {
        super("El proveedor de videoconsulta no está disponible en este momento. Probá de nuevo en unos minutos.");
    }
}
