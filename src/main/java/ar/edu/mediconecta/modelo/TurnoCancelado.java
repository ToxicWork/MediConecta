package ar.edu.mediconecta.modelo;

/** Estado final: el turno cancelado queda como historial (pago, autorización, auditoría) y no admite cambios. */
final class TurnoCancelado implements EstadoDeTurno {

    @Override
    public String descripcion() {
        return "cancelado";
    }
}
