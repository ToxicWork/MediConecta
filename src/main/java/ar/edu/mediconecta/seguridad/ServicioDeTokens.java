package ar.edu.mediconecta.seguridad;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Tokens JWT de la API REST dentro del contenedor: toma el secreto de firma de la configuración del servidor
 * y delega en {@link CodificadorJwt}.
 *
 * <p>El secreto nunca está en el repositorio. Se lee de la propiedad de sistema
 * {@code mediconecta.jwt.secreto} o de la variable de entorno {@code MEDICONECTA_JWT_SECRETO} (32 caracteres
 * como mínimo). Si no está configurado se genera uno al azar en cada arranque: la API funciona igual, pero los
 * tokens emitidos dejan de valer cuando se reinicia el servidor.
 */
@ApplicationScoped
public class ServicioDeTokens {

    private static final Logger LOG = Logger.getLogger(ServicioDeTokens.class.getName());
    static final Duration VIGENCIA = Duration.ofMinutes(30);

    private CodificadorJwt codificador;

    @PostConstruct
    void configurar() {
        String configurado = System.getProperty("mediconecta.jwt.secreto", System.getenv("MEDICONECTA_JWT_SECRETO"));
        byte[] secreto;
        if (configurado != null && configurado.getBytes(StandardCharsets.UTF_8).length >= 32) {
            secreto = configurado.getBytes(StandardCharsets.UTF_8);
            LOG.info("JWT: secreto de firma tomado de la configuración del servidor.");
        } else {
            secreto = new byte[32];
            new SecureRandom().nextBytes(secreto);
            LOG.warning("JWT: no hay un secreto configurado (mediconecta.jwt.secreto, 32 caracteres o más); "
                    + "se generó uno al azar y los tokens dejarán de valer al reiniciar el servidor.");
        }
        codificador = new CodificadorJwt(secreto, VIGENCIA, Clock.systemUTC());
    }

    public String emitir(String email, String rol) {
        return codificador.emitir(email, rol);
    }

    public Optional<IdentidadJwt> validar(String token) {
        return codificador.validar(token);
    }

    public long vigenciaEnSegundos() {
        return VIGENCIA.getSeconds();
    }
}
