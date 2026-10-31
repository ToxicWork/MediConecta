package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.modelo.Turno;

import java.time.LocalDateTime;

/** Lo que recorre la cadena de validación: el turno, quién lo pide y el instante de referencia. */
public record SolicitudReserva(Turno turno, Long pacienteId, LocalDateTime ahora) {
}
