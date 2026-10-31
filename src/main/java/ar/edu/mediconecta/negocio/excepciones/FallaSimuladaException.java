package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/** Falla inyectada a propósito desde el panel de integraciones para demostrar el rollback del flujo crítico. */
public class FallaSimuladaException extends ErrorDeNegocioException {

    public FallaSimuladaException() {
        super("Falla simulada al final de la confirmación, antes del commit: se revirtió todo "
                + "(turno, pago, solicitud de autorización y eventos) y el cobro se reembolsó automáticamente.");
    }
}
