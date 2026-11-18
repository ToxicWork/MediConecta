package ar.edu.mediconecta.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los tipos de problema (RFC 9457) y el Retry-After del 429. */
class ProblemasTest {

    @Test
    void elRetryAfterRedondeaHaciaArribaYNuncaEsCero() {
        assertEquals(900, Problemas.segundosParaReintentar(Duration.ofSeconds(899, 1)));
        assertEquals(2, Problemas.segundosParaReintentar(Duration.ofMillis(1500)));
        assertEquals(1, Problemas.segundosParaReintentar(Duration.ZERO));
    }

    @Test
    void cadaTipoTieneUnCodigoPropioYSuTypeLlevaASuExplicacion() {
        assertEquals(TipoDeProblema.values().length,
                Arrays.stream(TipoDeProblema.values()).map(TipoDeProblema::codigo).distinct().count());
        for (TipoDeProblema tipo : TipoDeProblema.values()) {
            assertTrue(tipo.uri().endsWith("problemas.md#" + tipo.codigo()), tipo.uri());
        }
    }

    /** El documento al que apunta cada {@code type} tiene que explicar ese problema. */
    @Test
    void docsApiProblemasExplicaCadaTipo() throws IOException {
        String documento = Files.readString(Path.of("docs", "api", "problemas.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n"); // en Windows, con core.autocrlf, el archivo puede tener CRLF
        for (TipoDeProblema tipo : TipoDeProblema.values()) {
            assertTrue(documento.contains("\n## " + tipo.codigo() + "\n"), "falta la sección de " + tipo.codigo());
        }
    }
}
