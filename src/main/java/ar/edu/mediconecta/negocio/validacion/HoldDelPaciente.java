package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;

public class HoldDelPaciente extends ValidadorReserva {

    @Override
    protected void verificar(SolicitudReserva solicitud) {
        if (solicitud.turno().getEstado() != EstadoTurno.HOLD || !solicitud.turno().perteneceAPaciente(solicitud.pacienteId())) {
            throw new ReservaInvalidaException("El turno ya no está retenido a tu nombre.");
        }
    }
}
