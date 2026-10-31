package ar.edu.mediconecta.modelo;

import java.time.LocalDateTime;

/** Estado concreto: horario publicado y libre. Sólo admite que un paciente lo retenga. */
final class TurnoDisponible implements EstadoDeTurno {

    @Override
    public String descripcion() {
        return "disponible";
    }

    @Override
    public EstadoTurno reservar(Turno turno, Paciente paciente, LocalDateTime venceEn) {
        turno.asignarPaciente(paciente, venceEn);
        return EstadoTurno.HOLD;
    }
}
