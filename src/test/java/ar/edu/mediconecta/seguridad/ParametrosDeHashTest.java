package ar.edu.mediconecta.seguridad;

import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.negocio.ServicioDeUsuariosBean;
import jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** ADR-0021: los hashes nuevos salen con PBKDF2-HMAC-SHA256 y 600.000 iteraciones, no con las 2048 por defecto. */
class ParametrosDeHashTest {

    @Test
    void usaLasIteracionesQueRecomiendaOwaspParaHmacSha256() {
        assertEquals("PBKDF2WithHmacSHA256", ParametrosDeHash.PBKDF2.get("Pbkdf2PasswordHash.Algorithm"));
        assertTrue(Integer.parseInt(ParametrosDeHash.PBKDF2.get("Pbkdf2PasswordHash.Iterations")) >= 600_000);
        assertTrue(Integer.parseInt(ParametrosDeHash.PBKDF2.get("Pbkdf2PasswordHash.SaltSizeBytes")) >= 16);
    }

    @Test
    void elIdentityStoreYLosServiciosUsanLosMismosParametros() {
        DatabaseIdentityStoreDefinition definicion =
                ConfiguracionSeguridad.class.getAnnotation(DatabaseIdentityStoreDefinition.class);
        String[] parametros = definicion.hashAlgorithmParameters();

        assertEquals(ParametrosDeHash.PBKDF2.size(), parametros.length);
        Arrays.stream(parametros).map(p -> p.split("=", 2))
                .forEach(p -> assertEquals(ParametrosDeHash.PBKDF2.get(p[0]), p[1], p[0]));
    }

    @Test
    void elServicioDeUsuariosGeneraLosHashesConEsosParametros() {
        Pbkdf2PasswordHash hash = mock(Pbkdf2PasswordHash.class);
        ServicioDeUsuariosBean servicio = Inyectar.en(new ServicioDeUsuariosBean(), "passwordHash", hash);

        Inyectar.invocar(servicio, "init");

        verify(hash).initialize(ParametrosDeHash.PBKDF2);
    }
}
