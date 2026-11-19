package ar.edu.mediconecta.seguridad;

/**
 * Lo que afirma un token válido: quién es el usuario, con qué rol, hasta cuándo (segundos Unix) y su identificador
 * único ({@code jti}), con el que se lo puede revocar antes de que venza.
 */
public record IdentidadJwt(String email, String rol, long venceEn, String jti) {
}
