package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;

public class TurnoLibre extends ValidadorReserva {

    @Override
    protected void verificar(SolicitudReserva solicitud) {
        if (solicitud.turno().getEstado() != EstadoTurno.DISPONIBLE) {
            throw new ReservaInvalidaException("El turno ya no está disponible.");
        }
    }
}
