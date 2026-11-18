package ar.edu.mediconecta.seguridad;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eje D: cabeceras de seguridad en todas las respuestas. */
class CabecerasDeSeguridadTest {

    @Test
    void todaRespuestaLlevaNosniffMarcosDenegadosYPermisosRestringidos() {
        Map<String, String> pagina = CabecerasDeSeguridad.cabeceras(false, true, 3600);

        assertEquals("nosniff", pagina.get("X-Content-Type-Options"));
        assertEquals("DENY", pagina.get("X-Frame-Options"));
        assertEquals("same-origin", pagina.get("Referrer-Policy"));
        assertTrue(pagina.get("Permissions-Policy").contains("camera=()"));
        assertTrue(pagina.get("Permissions-Policy").contains("microphone=()"));
    }

    @Test
    void hstsSoloPorHttpsYConLaDuracionConfigurada() {
        assertEquals("max-age=31536000", CabecerasDeSeguridad.cabeceras(false, true, 31536000).get("Strict-Transport-Security"));
        assertFalse(CabecerasDeSeguridad.cabeceras(false, false, 3600).containsKey("Strict-Transport-Security"));
    }

    /** La CSP de las páginas la pone PrimeFaces (con nonce): el filtro no tiene que agregar una segunda. */
    @Test
    void laApiTieneSuPropiaCspYNoSeCacheaPeroLasPaginasNo() {
        Map<String, String> api = CabecerasDeSeguridad.cabeceras(true, true, 3600);
        assertEquals(CabecerasDeSeguridad.CSP_DE_LA_API, api.get("Content-Security-Policy"));
        assertEquals("no-store", api.get("Cache-Control"));

        Map<String, String> pagina = CabecerasDeSeguridad.cabeceras(false, true, 3600);
        assertFalse(pagina.containsKey("Content-Security-Policy"));
    }
}
