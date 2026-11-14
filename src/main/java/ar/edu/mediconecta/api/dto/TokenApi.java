package ar.edu.mediconecta.api.dto;

/** Respuesta del login de la API: el JWT, cómo enviarlo ({@code Authorization: Bearer ...}) y cuánto dura. */
public record TokenApi(String token, String tipo, long expiraEnSegundos, String rol) {
}
