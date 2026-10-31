package ar.edu.mediconecta.modelo;

/**
 * Estado persistido del turno. Cada constante tiene asociada la clase que implementa su
 * comportamiento (patrón State): la entidad guarda el enum en la base y delega en
 * {@link #comportamiento()} las transiciones, de modo que una transición inválida (por ejemplo,
 * cancelar un turno disponible) es imposible por diseño y no depende de un switch repartido en
 * los servicios.
 */
public enum EstadoTurno {
    DISPONIBLE(new TurnoDisponible()),
    HOLD(new TurnoEnHold()),
    CONFIRMADO(new TurnoConfirmado()),
    CANCELADO(new TurnoCancelado());

    private final EstadoDeTurno comportamiento;

    EstadoTurno(EstadoDeTurno comportamiento) {
        this.comportamiento = comportamiento;
    }

    EstadoDeTurno comportamiento() {
        return comportamiento;
    }

    public String getEtiqueta() {
        return comportamiento.descripcion();
    }
}
