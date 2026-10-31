package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;

public class HorarioFuturo extends ValidadorReserva {

    @Override
    protected void verificar(SolicitudReserva solicitud) {
        if (!solicitud.turno().getFechaHora().isAfter(solicitud.ahora())) {
            throw new ReservaInvalidaException("El horario del turno ya pasó.");
        }
    }
}
