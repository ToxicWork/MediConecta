package ar.edu.mediconecta.api;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ETag e If-Match / If-None-Match del turno (Eje C: actualización concurrente con ETag desactualizado → 412). */
class VersionesTest {

    @Test
    void laEtiquetaEsLaVersionEntreComillas() {
        assertEquals("\"7\"", Versiones.etiqueta(7));
    }

    @Test
    void sinIfMatchOConAsteriscoNoHayPrecondicion() {
        assertTrue(Versiones.aceptadasPorIfMatch(null).isEmpty());
        assertTrue(Versiones.aceptadasPorIfMatch(" ").isEmpty());
        assertTrue(Versiones.aceptadasPorIfMatch("*").isEmpty());
    }

    @Test
    void ifMatchAceptaUnaOVariasEtiquetas() {
        assertEquals(Optional.of(Set.of(7)), Versiones.aceptadasPorIfMatch("\"7\""));
        assertEquals(Optional.of(Set.of(7, 8)), Versiones.aceptadasPorIfMatch("\"7\", \"8\""));
    }

    /** If-Match compara en forma fuerte: una etiqueta débil, o que no es nuestra, no coincide nunca (412). */
    @Test
    void unaEtiquetaDebilOAjenaNoCoincideNunca() {
        assertEquals(Optional.of(Set.of()), Versiones.aceptadasPorIfMatch("W/\"7\""));
        assertEquals(Optional.of(Set.of()), Versiones.aceptadasPorIfMatch("\"abc\""));
        assertEquals(Optional.of(Set.of()), Versiones.aceptadasPorIfMatch("7"));
    }

    @Test
    void ifNoneMatchComparaEnFormaDebil() {
        assertTrue(Versiones.coincideIfNoneMatch("\"7\"", 7));
        assertTrue(Versiones.coincideIfNoneMatch("W/\"7\"", 7));
        assertTrue(Versiones.coincideIfNoneMatch("\"3\", \"7\"", 7));
        assertTrue(Versiones.coincideIfNoneMatch("*", 7));
        assertFalse(Versiones.coincideIfNoneMatch("\"6\"", 7));
        assertFalse(Versiones.coincideIfNoneMatch(null, 7));
    }
}
