package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;

public class HoldVigente extends ValidadorReserva {

    @Override
    protected void verificar(SolicitudReserva solicitud) {
        var vence = solicitud.turno().getHoldVenceEn();
        if (vence != null && !solicitud.ahora().isBefore(vence)) {
            throw new ReservaInvalidaException("Venció el plazo de 5 minutos para confirmar el turno.");
        }
    }
}
