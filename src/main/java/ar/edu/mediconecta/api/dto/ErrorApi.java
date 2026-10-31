package ar.edu.mediconecta.api.dto;

import java.time.LocalDateTime;

/** Cuerpo de error uniforme: nunca incluye trazas ni nombres internos. */
public record ErrorApi(int estado, String error, String mensaje, LocalDateTime fecha) {

    public static ErrorApi de(int estado, String error, String mensaje) {
        return new ErrorApi(estado, error, mensaje, LocalDateTime.now());
    }
}
