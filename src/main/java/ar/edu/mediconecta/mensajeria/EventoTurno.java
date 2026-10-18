package ar.edu.mediconecta.mensajeria;

import ar.edu.mediconecta.modelo.Turno;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Mensaje que viaja por {@code topico.turnos}. Lleva sólo datos de contacto y del turno:
 * nunca diagnósticos ni datos clínicos (minimización de datos en el broker).
 *
 * <p>Se arma con {@link Builder} (patrón Builder): {@code build()} valida lo obligatorio y
 * genera siempre el {@code idEvento}, del que depende la idempotencia de los suscriptores.
 */
public record EventoTurno(
        String idEvento,
        TipoEventoTurno tipo,
        LocalDateTime ocurridoEn,
        Long turnoId,
        LocalDateTime fechaHoraTurno,
        Long profesionalId,
        String profesionalNombre,
        String especialidad,
        Long pacienteId,
        String pacienteNombre,
        String pacienteEmail) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private TipoEventoTurno tipo;
        private Turno turno;

        private Builder() {
        }

        public Builder tipo(TipoEventoTurno tipo) {
            this.tipo = tipo;
            return this;
        }

        public Builder turno(Turno turno) {
            this.turno = turno;
            return this;
        }

        public EventoTurno build() {
            Objects.requireNonNull(tipo, "El evento necesita un tipo");
            Objects.requireNonNull(turno, "El evento necesita un turno");
            Objects.requireNonNull(turno.getPaciente(), "El turno del evento debe tener paciente");
            return new EventoTurno(
                    UUID.randomUUID().toString(),
                    tipo,
                    LocalDateTime.now(),
                    turno.getId(),
                    turno.getFechaHora(),
                    turno.getProfesional().getId(),
                    turno.getProfesional().getNombreCompleto(),
                    turno.getProfesional().getEspecialidad(),
                    turno.getPaciente().getId(),
                    turno.getPaciente().getNombreCompleto(),
                    turno.getPaciente().getEmail());
        }
    }
}
