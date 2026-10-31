package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/** Error de sistema dentro del flujo de confirmación, ya revertido. Se convierte para no perder la conversación. */
public class ConfirmacionFallidaException extends ErrorDeNegocioException {

    public ConfirmacionFallidaException() {
        super("No pudimos confirmar el turno por un error del sistema. La operación se revirtió por completo "
                + "(si se había cobrado, el importe se reintegró). Tu turno sigue en espera: probá de nuevo.");
    }
}
