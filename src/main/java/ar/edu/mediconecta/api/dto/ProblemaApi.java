package ar.edu.mediconecta.api.dto;

import jakarta.json.bind.annotation.JsonbPropertyOrder;

import java.time.LocalDateTime;

/**
 * Cuerpo de todo error de la API en formato Problem Details (RFC 9457), con el tipo de contenido
 * {@code application/problem+json}: {@code type} (identifica el problema y lleva a su explicación), {@code title},
 * {@code status}, {@code detail} e {@code instance} (la ruta del pedido). Nunca incluye trazas ni nombres internos.
 *
 * <p>Como extensiones (el RFC las admite) conserva los campos del formato anterior, {@code estado}, {@code error},
 * {@code mensaje} y {@code fecha}, documentados en docs/api/README.md: los clientes que ya los leían siguen
 * funcionando.
 */
@JsonbPropertyOrder({"type", "title", "status", "detail", "instance", "estado", "error", "mensaje", "fecha"})
public record ProblemaApi(String type, String title, int status, String detail, String instance,
                          int estado, String error, String mensaje, LocalDateTime fecha) {
}
