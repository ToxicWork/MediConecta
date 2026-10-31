package ar.edu.mediconecta.modelo;

import java.time.LocalDateTime;

/**
 * Patrón State: operaciones del turno que dependen de su estado. Por defecto toda transición es
 * inválida; cada estado concreto redefine sólo las que admite y devuelve el estado siguiente.
 */
interface EstadoDeTurno {

    String descripcion();

    default EstadoTurno reservar(Turno turno, Paciente paciente, LocalDateTime venceEn) {
        throw invalida("reservar");
    }

    default EstadoTurno confirmar(Turno turno, DatosConfirmacion datos) {
        throw invalida("confirmar");
    }

    default EstadoTurno cancelar(Turno turno) {
        throw invalida("cancelar");
    }

    default EstadoTurno liberar(Turno turno) {
        throw invalida("liberar");
    }

    private TransicionInvalidaException invalida(String operacion) {
        return new TransicionInvalidaException("No se puede " + operacion + " un turno " + descripcion() + ".");
    }
}
