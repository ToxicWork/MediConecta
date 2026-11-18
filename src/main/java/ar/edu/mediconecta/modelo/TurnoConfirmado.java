package ar.edu.mediconecta.modelo;

import java.time.LocalDateTime;

/**
 * Estado concreto: confirmado. Sólo se puede cancelar, y sólo antes de la hora del turno: uno que ya empezó se está
 * atendiendo o ya se atendió, y cancelarlo dispararía el reintegro de un copago por una consulta realizada (D-13).
 */
final class TurnoConfirmado implements EstadoDeTurno {

    static final String YA_EMPEZO = "El turno ya empezó: no se puede cancelar.";

    @Override
    public String descripcion() {
        return "confirmado";
    }

    @Override
    public boolean admiteCancelacion(Turno turno, LocalDateTime ahora) {
        return turno.getFechaHora().isAfter(ahora);
    }

    @Override
    public EstadoTurno cancelar(Turno turno, LocalDateTime ahora) {
        if (!admiteCancelacion(turno, ahora)) {
            throw new TransicionInvalidaException(YA_EMPEZO);
        }
        turno.marcarCancelado(ahora);
        return EstadoTurno.CANCELADO;
    }
}
