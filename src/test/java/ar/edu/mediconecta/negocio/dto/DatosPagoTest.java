package ar.edu.mediconecta.negocio.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-19: un único mensaje, claro y bien escrito, con todo lo que hay que corregir en la tarjeta. */
class DatosPagoTest {

    private static DatosPago tarjeta(String titular, String numero, String vencimiento, String codigo) {
        DatosPago datos = new DatosPago();
        datos.setTitular(titular);
        datos.setNumeroTarjeta(numero);
        datos.setVencimiento(vencimiento);
        datos.setCodigoSeguridad(codigo);
        return datos;
    }

    private static DatosPago valida() {
        return tarjeta("Juan Perez", "4111 1111 1111 1111", "12/29", "123");
    }

    @Test
    void unaTarjetaCompletaYBienFormadaNoTieneErrores() {
        assertEquals(Optional.empty(), valida().mensajeDeError());
        assertTrue(valida().camposConError().isEmpty());
    }

    /** El caso del defecto: con el código vacío había dos carteles ("ingresá el código" y "tiene 3 o 4 dígitos"). */
    @Test
    void siFaltaElCodigoSoloDiceQueFalta() {
        DatosPago datos = tarjeta("Juan Perez", "4111 1111 1111 1111", "12/29", "");
        assertEquals(Optional.of("Revisá los datos de la tarjeta: falta el código de seguridad."), datos.mensajeDeError());
        assertEquals(Set.of("codigoSeguridad"), datos.camposConError());
    }

    @Test
    void siFaltaTodoLoNombraEnUnaSolaFrase() {
        assertEquals("Revisá los datos de la tarjeta: faltan el nombre del titular, el número de la tarjeta, "
                        + "el vencimiento y el código de seguridad.",
                tarjeta(null, " ", "", null).mensajeDeError().orElseThrow());
    }

    @Test
    void primeroLoQueFaltaYDespuesLoQueEstaMal() {
        DatosPago datos = tarjeta("", "4111 1111 1111 1111", "0228", "12");
        assertEquals("Revisá los datos de la tarjeta: falta el nombre del titular; el vencimiento va en formato MM/AA "
                + "y el código de seguridad tiene 3 o 4 dígitos.", datos.mensajeDeError().orElseThrow());
        assertEquals(Set.of("titular", "vencimiento", "codigoSeguridad"), datos.camposConError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"4111111111111", "4111 1111 1111 1111", "4111111111111111111", " 4111 1111 1111 1111 "})
    void aceptaDe13A19DigitosConEspaciosOpcionales(String numero) {
        assertEquals(Optional.empty(), tarjeta("Juan Perez", numero, "12/29", "123").mensajeDeError());
    }

    @ParameterizedTest
    @ValueSource(strings = {"411111111111", "41111111111111111111", "4111-1111-1111-1111", "4111 1111 1111 111a"})
    void rechazaNumerosFueraDeRango(String numero) {
        assertEquals("Revisá los datos de la tarjeta: el número de la tarjeta tiene entre 13 y 19 dígitos.",
                tarjeta("Juan Perez", numero, "12/29", "123").mensajeDeError().orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"13/29", "00/29", "1/29", "12-29", "12/2029"})
    void elVencimientoEsMesYAnioConBarra(String vencimiento) {
        assertEquals("Revisá los datos de la tarjeta: el vencimiento va en formato MM/AA.",
                tarjeta("Juan Perez", "4111 1111 1111 1111", vencimiento, "123").mensajeDeError().orElseThrow());
    }

    @Test
    void elTitularAdmiteHasta80Caracteres() {
        assertEquals(Optional.empty(), tarjeta("a".repeat(80), "4111 1111 1111 1111", "12/29", "123").mensajeDeError());
        assertEquals("Revisá los datos de la tarjeta: el nombre del titular admite hasta 80 caracteres.",
                tarjeta("a".repeat(81), "4111 1111 1111 1111", "12/29", "123").mensajeDeError().orElseThrow());
    }

    @Test
    void losUltimosDigitosSonLosDelNumeroSinEspacios() {
        assertEquals("1111", valida().getUltimosDigitos());
        assertEquals("", tarjeta("x", null, "12/29", "123").getUltimosDigitos());
    }
}
