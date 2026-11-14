package ar.edu.mediconecta.seguridad;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Emite y valida JSON Web Tokens (RFC 7519) firmados con HMAC-SHA256 (HS256). No depende del contenedor:
 * recibe el secreto, la vigencia y el reloj, de modo que se prueba con JUnit sin levantar el servidor.
 *
 * <p>Un token son tres partes en Base64URL separadas por puntos: {@code cabecera.contenido.firma}. La firma
 * es el HMAC de las dos primeras con un secreto que sólo conoce el servidor; por eso el servidor no guarda
 * sesión: le alcanza con recalcular la firma para saber que el contenido (usuario, rol y vencimiento) lo
 * emitió él y nadie lo tocó.
 *
 * <p>Reglas de validación: el algoritmo de la cabecera tiene que ser exactamente HS256 (se rechaza
 * {@code alg: none}), la firma se compara en tiempo constante, y se exigen el emisor y un vencimiento futuro.
 */
public final class CodificadorJwt {

    public static final String EMISOR = "mediconecta";

    private static final String ALGORITMO = "HmacSHA256";
    private static final String CABECERA = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODIFICADOR = Base64.getUrlDecoder();
    private static final int LARGO_MINIMO_SECRETO = 32; // 256 bits, lo que pide HS256 (RFC 7518, 3.2)

    private final byte[] secreto;
    private final Duration vigencia;
    private final Clock reloj;

    public CodificadorJwt(byte[] secreto, Duration vigencia, Clock reloj) {
        if (secreto == null || secreto.length < LARGO_MINIMO_SECRETO) {
            throw new IllegalArgumentException("El secreto de firma debe tener al menos 32 bytes.");
        }
        this.secreto = secreto.clone();
        this.vigencia = vigencia;
        this.reloj = reloj;
    }

    public Duration vigencia() {
        return vigencia;
    }

    /** Emite un token para el usuario (su email) con su rol, válido durante {@link #vigencia()}. */
    public String emitir(String email, String rol) {
        long ahora = reloj.instant().getEpochSecond();
        String contenido = "{\"iss\":\"" + EMISOR + "\""
                + ",\"sub\":\"" + escapar(email) + "\""
                + ",\"rol\":\"" + escapar(rol) + "\""
                + ",\"iat\":" + ahora
                + ",\"exp\":" + (ahora + vigencia.getSeconds())
                + ",\"jti\":\"" + UUID.randomUUID() + "\"}";
        String aFirmar = codificar(CABECERA) + "." + codificar(contenido);
        return aFirmar + "." + B64.encodeToString(firmar(aFirmar));
    }

    /** Devuelve la identidad que afirma el token, o vacío si está mal formado, alterado o vencido. */
    public Optional<IdentidadJwt> validar(String token) {
        if (token == null) {
            return Optional.empty();
        }
        String[] partes = token.trim().split("\\.", -1);
        if (partes.length != 3) {
            return Optional.empty();
        }
        try {
            String cabecera = decodificar(partes[0]);
            if (!"HS256".equals(texto(cabecera, "alg"))) {
                return Optional.empty();
            }
            byte[] firmaRecibida = B64_DECODIFICADOR.decode(partes[2]);
            byte[] firmaEsperada = firmar(partes[0] + "." + partes[1]);
            if (!MessageDigest.isEqual(firmaEsperada, firmaRecibida)) {
                return Optional.empty();
            }
            String contenido = decodificar(partes[1]);
            String email = texto(contenido, "sub");
            String rol = texto(contenido, "rol");
            Long vence = numero(contenido, "exp");
            if (email == null || rol == null || vence == null || !EMISOR.equals(texto(contenido, "iss"))) {
                return Optional.empty();
            }
            if (reloj.instant().getEpochSecond() >= vence) {
                return Optional.empty();
            }
            return Optional.of(new IdentidadJwt(email, rol, vence));
        } catch (IllegalArgumentException malFormado) {
            return Optional.empty();
        }
    }

    private byte[] firmar(String datos) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(secreto, ALGORITMO));
            return mac.doFinal(datos.getBytes(StandardCharsets.US_ASCII));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 no disponible", e);
        }
    }

    private static String codificar(String json) {
        return B64.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodificar(String parte) {
        return new String(B64_DECODIFICADOR.decode(parte), StandardCharsets.UTF_8);
    }

    private static String escapar(String valor) {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Lee un campo de texto del JSON. Sólo se usa sobre contenido cuya firma ya se verificó (o la cabecera). */
    private static String texto(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : null;
    }

    private static Long numero(String json, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\"\\s*:\\s*(\\d{1,15})").matcher(json);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }
}
