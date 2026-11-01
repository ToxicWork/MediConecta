package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/** Datos de alta o de cambio de contraseña que no cumplen las reglas (se informan al usuario tal cual). */
public class DatosDeUsuarioInvalidosException extends ErrorDeNegocioException {

    public DatosDeUsuarioInvalidosException(String mensaje) {
        super(mensaje);
    }
}
