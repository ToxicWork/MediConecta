package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;

public class TurnoExistente extends ValidadorReserva {

    @Override
    protected void verificar(SolicitudReserva solicitud) {
        if (solicitud.turno() == null) {
            throw new ReservaInvalidaException("El turno no existe.");
        }
    }
}
