package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/** Un parámetro o una cabecera del pedido con un formato que no sirve. La API responde 400. */
public class ParametroInvalidoException extends ErrorDeNegocioException {

    public ParametroInvalidoException(String mensaje) {
        super(mensaje);
    }
}
