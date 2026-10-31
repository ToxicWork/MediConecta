package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

public class DatosDePagoInvalidosException extends ErrorDeNegocioException {

    public DatosDePagoInvalidosException(String mensaje) {
        super("Datos de pago inválidos: " + mensaje);
    }
}
