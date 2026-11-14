package ar.edu.mediconecta.seguridad;

/** Lo que afirma un token válido: quién es el usuario, con qué rol y hasta cuándo (segundos Unix). */
public record IdentidadJwt(String email, String rol, long venceEn) {
}
