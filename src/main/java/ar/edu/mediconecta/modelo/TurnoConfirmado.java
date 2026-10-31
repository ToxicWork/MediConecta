package ar.edu.mediconecta.modelo;

/** Estado concreto: confirmado. Sólo se puede cancelar (el horario se republica como un turno nuevo). */
final class TurnoConfirmado implements EstadoDeTurno {

    @Override
    public String descripcion() {
        return "confirmado";
    }

    @Override
    public EstadoTurno cancelar(Turno turno) {
        turno.marcarCancelado();
        return EstadoTurno.CANCELADO;
    }
}
