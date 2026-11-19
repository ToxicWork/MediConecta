package ar.edu.mediconecta.seguridad;

import java.util.Map;

/**
 * Los parámetros de PBKDF2 con los que se generan los hashes de contraseña (ADR-0021): HMAC-SHA256, sal de 32 bytes y
 * 600.000 iteraciones, el mínimo que recomienda OWASP para ese algoritmo (Password Storage Cheat Sheet).
 *
 * <p>Sin parámetros, la implementación de Jakarta Security de WildFly (Soteria 4.0.2) usa 2048 iteraciones, unas 290
 * veces menos: medido en una PC de desarrollo, 0,94 ms por hash contra 120 ms. Cada hash guarda sus propias
 * iteraciones ("PBKDF2WithHmacSHA256:600000:sal:hash"), así que los generados antes se siguen verificando y pasan a
 * 600.000 cuando el usuario cambia la contraseña.
 */
public final class ParametrosDeHash {

    public static final String ALGORITMO = "Pbkdf2PasswordHash.Algorithm=PBKDF2WithHmacSHA256";
    public static final String ITERACIONES = "Pbkdf2PasswordHash.Iterations=600000";
    public static final String SAL = "Pbkdf2PasswordHash.SaltSizeBytes=32";

    /** Los mismos parámetros, como los recibe {@code Pbkdf2PasswordHash.initialize}. */
    public static final Map<String, String> PBKDF2 = Map.of(
            "Pbkdf2PasswordHash.Algorithm", "PBKDF2WithHmacSHA256",
            "Pbkdf2PasswordHash.Iterations", "600000",
            "Pbkdf2PasswordHash.SaltSizeBytes", "32");

    private ParametrosDeHash() {
    }
}
