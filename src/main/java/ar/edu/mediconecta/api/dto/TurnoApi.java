package ar.edu.mediconecta.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Transfer Object de un turno para la API: sin datos clínicos ni campos internos de la entidad. */
public record TurnoApi(Long id, LocalDateTime fechaHora, String modalidad, String estado, String profesional,
                       String especialidad, String paciente, String cobertura, String autorizacion,
                       String codigoAutorizacion, BigDecimal importePaciente, String salaVideoconsulta) {
}
