package ar.edu.mediconecta.seguridad;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JWT sin contenedor: emisión, firma, vencimiento con reloj controlado y los ataques típicos. */
class CodificadorJwtTest {

    private static final byte[] SECRETO = "secreto-de-prueba-de-32-bytes-o-mas!".getBytes(StandardCharsets.UTF_8);
    private static final Instant AHORA = Instant.parse("2026-11-16T13:30:00Z");
    private static final Duration MEDIA_HORA = Duration.ofMinutes(30);

    private static CodificadorJwt codificador(Instant instante) {
        return new CodificadorJwt(SECRETO, MEDIA_HORA, Clock.fixed(instante, ZoneOffset.UTC));
    }

    @Test
    void unTokenEmitidoSeValidaYDevuelveUsuarioRolYVencimiento() {
        String token = codificador(AHORA).emitir("juan.perez@mediconecta.test", "PACIENTE");

        assertEquals(3, token.split("\\.").length, "cabecera.contenido.firma");
        Optional<IdentidadJwt> identidad = codificador(AHORA.plusSeconds(60)).validar(token);

        assertTrue(identidad.isPresent());
        assertEquals("juan.perez@mediconecta.test", identidad.get().email());
        assertEquals("PACIENTE", identidad.get().rol());
        assertEquals(AHORA.plus(MEDIA_HORA).getEpochSecond(), identidad.get().venceEn());
    }

    /** Cada token lleva su identificador (jti), distinto en cada emisión: con él se lo revoca al cerrar sesión. */
    @Test
    void cadaTokenTraeSuPropioIdentificador() {
        CodificadorJwt codificador = codificador(AHORA);
        String jti = codificador.validar(codificador.emitir("juan@test", "PACIENTE")).orElseThrow().jti();
        String otro = codificador.validar(codificador.emitir("juan@test", "PACIENTE")).orElseThrow().jti();

        assertTrue(jti.matches("[0-9a-f-]{36}"), jti);
        assertFalse(jti.equals(otro), "dos tokens del mismo usuario no comparten jti");
    }

    @Test
    void unTokenVencidoSeRechaza() {
        String token = codificador(AHORA).emitir("juan@test", "PACIENTE");

        assertTrue(codificador(AHORA.plus(MEDIA_HORA).minusSeconds(1)).validar(token).isPresent());
        assertFalse(codificador(AHORA.plus(MEDIA_HORA)).validar(token).isPresent(), "vence justo a los 30 minutos");
    }

    @Test
    void cambiarElRolDelContenidoInvalidaLaFirma() {
        String[] partes = codificador(AHORA).emitir("juan@test", "PACIENTE").split("\\.");
        String contenido = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
        String adulterado = b64(contenido.replace("PACIENTE", "ADMINISTRATIVO"));

        assertFalse(codificador(AHORA).validar(partes[0] + "." + adulterado + "." + partes[2]).isPresent());
    }

    @Test
    void unTokenFirmadoConOtroSecretoSeRechaza() {
        byte[] otro = "otro-secreto-distinto-de-32-bytes-o-mas".getBytes(StandardCharsets.UTF_8);
        String ajeno = new CodificadorJwt(otro, MEDIA_HORA, Clock.fixed(AHORA, ZoneOffset.UTC)).emitir("juan@test", "PACIENTE");

        assertFalse(codificador(AHORA).validar(ajeno).isPresent());
    }

    @Test
    void elAlgoritmoNoneSeRechazaAunqueElContenidoSeaValido() {
        String[] partes = codificador(AHORA).emitir("juan@test", "ADMINISTRATIVO").split("\\.");
        String cabeceraNone = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");

        assertFalse(codificador(AHORA).validar(cabeceraNone + "." + partes[1] + ".").isPresent());
        assertFalse(codificador(AHORA).validar(cabeceraNone + "." + partes[1] + "." + partes[2]).isPresent());
    }

    @Test
    void loQueNoEsUnTokenSeRechazaSinExcepciones() {
        CodificadorJwt jwt = codificador(AHORA);

        assertFalse(jwt.validar(null).isPresent());
        assertFalse(jwt.validar("").isPresent());
        assertFalse(jwt.validar("no-es-un-token").isPresent());
        assertFalse(jwt.validar("a.b").isPresent());
        assertFalse(jwt.validar("%%%.%%%.%%%").isPresent());
        assertFalse(jwt.validar("a.b.c.d").isPresent());
    }

    @Test
    void dosTokensDelMismoUsuarioSonDistintos() {
        CodificadorJwt jwt = codificador(AHORA);

        assertFalse(jwt.emitir("juan@test", "PACIENTE").equals(jwt.emitir("juan@test", "PACIENTE")), "cada uno lleva su jti");
    }

    @Test
    void unSecretoCortoNoSeAcepta() {
        assertThrows(IllegalArgumentException.class,
                () -> new CodificadorJwt("corto".getBytes(StandardCharsets.UTF_8), MEDIA_HORA, Clock.systemUTC()));
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
