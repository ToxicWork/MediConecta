package ar.edu.mediconecta.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TurnoDisponibleApi(Long id, LocalDateTime fechaHora, String modalidad, String codigoPrestacion,
                                 BigDecimal precioLista) {
}
