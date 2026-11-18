package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.ProblemaApi;
import ar.edu.mediconecta.seguridad.MecanismoAutenticacion;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.time.LocalDateTime;

/** Arma los errores de la API en formato Problem Details (RFC 9457) y sus respuestas HTTP. */
public final class Problemas {

    public static final String TIPO_DE_CONTENIDO = "application/problem+json";

    private Problemas() {
    }

    public static ProblemaApi de(TipoDeProblema tipo, String detalle, String instancia) {
        return de(tipo, tipo.estado(), tipo.titulo(), detalle, instancia);
    }

    static ProblemaApi de(TipoDeProblema tipo, int estado, String titulo, String detalle, String instancia) {
        return new ProblemaApi(tipo.uri(), titulo, estado, detalle, instancia,
                estado, tipo.codigo(), detalle, LocalDateTime.now());
    }

    /**
     * La respuesta con el problema y su tipo de contenido. Todo 401 lleva el desafío {@code WWW-Authenticate: Bearer}
     * (RFC 6750); el de un token inválido, además, {@code error="invalid_token"}.
     */
    public static Response.ResponseBuilder respuesta(ProblemaApi problema) {
        Response.ResponseBuilder respuesta = Response.status(problema.status()).type(TIPO_DE_CONTENIDO).entity(problema);
        if (problema.status() == 401) {
            respuesta.header("WWW-Authenticate", TipoDeProblema.TOKEN_INVALIDO.codigo().equals(problema.error())
                    ? MecanismoAutenticacion.DESAFIO_BEARER + ", error=\"invalid_token\""
                    : MecanismoAutenticacion.DESAFIO_BEARER);
        }
        return respuesta;
    }

    /** 429 con {@code Retry-After}: los segundos que faltan, redondeados hacia arriba. */
    public static Response.ResponseBuilder demasiadosIntentos(Duration espera, String instancia) {
        long segundos = segundosParaReintentar(espera);
        long minutos = (segundos + 59) / 60;
        return respuesta(de(TipoDeProblema.DEMASIADOS_INTENTOS, "Demasiados intentos fallidos con este email: probá de "
                + "nuevo en " + minutos + (minutos == 1 ? " minuto." : " minutos."), instancia))
                .header("Retry-After", String.valueOf(segundos));
    }

    public static long segundosParaReintentar(Duration espera) {
        long segundos = espera.getSeconds() + (espera.getNano() > 0 ? 1 : 0);
        return Math.max(1, segundos);
    }
}
