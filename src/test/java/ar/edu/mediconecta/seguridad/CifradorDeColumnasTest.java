package ar.edu.mediconecta.seguridad;

import ar.edu.mediconecta.seguridad.CifradorDeColumnas.DatoCifradoInvalidoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADR-0022: el n.º de afiliado y la historia clínica se guardan cifrados con AES-256-GCM (diseño, sección 8.6). */
class CifradorDeColumnasTest {

    private static final String DIAGNOSTICO = "Hipertensión arterial leve; control en 30 días. Enalapril 10 mg.";

    private final CifradorDeColumnas cifrador = new CifradorDeColumnas(clave());

    static byte[] clave() {
        byte[] clave = new byte[32];
        new SecureRandom().nextBytes(clave);
        return clave;
    }

    @AfterEach
    void olvidarLaClave() {
        ClaveDeCifrado.usar(null);
    }

    @Test
    void loQueSeCifraSeDescifraIgualConAcentosYTodo() {
        String guardado = cifrador.cifrar(DIAGNOSTICO);

        assertTrue(guardado.startsWith(CifradorDeColumnas.PREFIJO));
        assertFalse(guardado.contains("Hipertensión"), "en la base no queda nada legible");
        assertEquals(DIAGNOSTICO, cifrador.descifrar(guardado));
        assertEquals("", cifrador.descifrar(cifrador.cifrar("")));
    }

    @Test
    void elMismoTextoDaUnResultadoDistintoCadaVez() {
        String primero = cifrador.cifrar("210-123456-01");
        String segundo = cifrador.cifrar("210-123456-01");

        assertNotEquals(primero, segundo, "con un IV al azar no se puede ver en la base quién comparte un dato");
        assertEquals(cifrador.descifrar(primero), cifrador.descifrar(segundo));
    }

    @Test
    void siAlguienModificaElDatoEnLaBaseSeDetecta() {
        String guardado = cifrador.cifrar(DIAGNOSTICO);
        byte[] bytes = Base64.getDecoder().decode(guardado.substring(CifradorDeColumnas.PREFIJO.length()));
        bytes[20] ^= 1; // un solo bit del texto cifrado
        String alterado = CifradorDeColumnas.PREFIJO + Base64.getEncoder().encodeToString(bytes);

        DatoCifradoInvalidoException error = assertThrows(DatoCifradoInvalidoException.class, () -> cifrador.descifrar(alterado));
        assertTrue(error.getMessage().contains("alterado"));
    }

    @Test
    void conOtraClaveNoSeDescifra() {
        String guardado = cifrador.cifrar(DIAGNOSTICO);

        assertThrows(DatoCifradoInvalidoException.class, () -> new CifradorDeColumnas(clave()).descifrar(guardado));
    }

    @Test
    void unDatoCifradoRotoOIncompletoSeRechaza() {
        assertThrows(DatoCifradoInvalidoException.class, () -> cifrador.descifrar(CifradorDeColumnas.PREFIJO + "no es base64!"));
        assertThrows(DatoCifradoInvalidoException.class, () -> cifrador.descifrar(CifradorDeColumnas.PREFIJO + "AAAA"));
    }

    @Test
    void loDeAntesDelCifradoYLosNulosPasanSinCambios() {
        assertEquals("210-123", cifrador.descifrar("210-123"));
        assertNull(cifrador.descifrar(null));
        assertNull(cifrador.cifrar(null));
        assertFalse(CifradorDeColumnas.estaCifrado("210-123"));
        assertFalse(CifradorDeColumnas.estaCifrado(null));
    }

    @Test
    void laClaveTieneQueSerDe32BytesEnBase64() {
        assertThrows(IllegalArgumentException.class, () -> new CifradorDeColumnas(new byte[16]));
        assertThrows(IllegalArgumentException.class, () -> new CifradorDeColumnas(null));
        assertThrows(IllegalArgumentException.class, () -> CifradorDeColumnas.deBase64("no-es-base64!"));
        String base64 = Base64.getEncoder().encodeToString(clave());
        assertEquals("x", CifradorDeColumnas.deBase64(" " + base64 + "\n").descifrar(CifradorDeColumnas.deBase64(base64).cifrar("x")));
    }

    @Test
    void laClaveSaleDeLaPropiedadDelServidorODeLaVariableDeEntorno() {
        String base64 = Base64.getEncoder().encodeToString(clave());
        CifradorDeColumnas dePropiedad = ClaveDeCifrado.desdeConfiguracion(Map.of(ClaveDeCifrado.PROPIEDAD, base64)::get, n -> null);
        CifradorDeColumnas deEntorno = ClaveDeCifrado.desdeConfiguracion(n -> null, Map.of(ClaveDeCifrado.VARIABLE, base64)::get);

        assertEquals("ok", deEntorno.descifrar(dePropiedad.cifrar("ok")), "las dos fuentes dan la misma clave");
    }

    @Test
    void sinClaveOConLaExpresionDeElytronSinResolverNoArranca() {
        IllegalStateException sinClave = assertThrows(IllegalStateException.class,
                () -> ClaveDeCifrado.desdeConfiguracion(n -> null, n -> " "));
        assertTrue(sinClave.getMessage().contains("configurar_cifrado.py"));

        IllegalStateException sinResolver = assertThrows(IllegalStateException.class,
                () -> ClaveDeCifrado.desdeConfiguracion(n -> "${ENC::mediconecta:RUxZAUMQ...}", n -> null));
        assertFalse(sinResolver.getMessage().contains("RUxZ"), "el mensaje no repite la expresión cifrada");
    }

    @Test
    void elConversorDeJpaCifraAlGuardarYDescifraAlLeer() {
        ClaveDeCifrado.usar(cifrador);
        CifradoConverter conversor = new CifradoConverter();

        String enLaBase = conversor.convertToDatabaseColumn("210-123456-01");

        assertTrue(CifradorDeColumnas.estaCifrado(enLaBase));
        assertEquals("210-123456-01", conversor.convertToEntityAttribute(enLaBase));
        assertEquals("210-123", conversor.convertToEntityAttribute("210-123"), "las filas viejas se siguen leyendo");
        assertNull(conversor.convertToDatabaseColumn(null));
        assertNull(conversor.convertToEntityAttribute(null));
        assertSame(cifrador, ClaveDeCifrado.cifrador());
    }
}
