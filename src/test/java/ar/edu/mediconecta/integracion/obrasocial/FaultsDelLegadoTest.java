package ar.edu.mediconecta.integracion.obrasocial;

import org.junit.jupiter.api.Test;

import javax.xml.namespace.QName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Eje C: un SOAP Fault de negocio no es una falla técnica, y al usuario nunca le llega su texto. */
class FaultsDelLegadoTest {

    private static final String SOAP_11 = "http://schemas.xmlsoap.org/soap/envelope/";
    private static final String SOAP_12 = "http://www.w3.org/2003/05/soap-envelope";

    @Test
    void clientEnSoap11YSenderEnSoap12SonDeNegocio() {
        assertTrue(FaultsDelLegado.esDeNegocio(new QName(SOAP_11, "Client")));
        assertTrue(FaultsDelLegado.esDeNegocio(new QName(SOAP_12, "Sender")));
    }

    @Test
    void serverReceiverYLosDeSeguridadSonTecnicos() {
        assertFalse(FaultsDelLegado.esDeNegocio(new QName(SOAP_11, "Server")));
        assertFalse(FaultsDelLegado.esDeNegocio(new QName(SOAP_12, "Receiver")));
        assertFalse(FaultsDelLegado.esDeNegocio(new QName("http://docs.oasis-open.org/wss/2004/01/x", "FailedAuthentication")));
        assertFalse(FaultsDelLegado.esDeNegocio(null));
    }

    @Test
    void elMensajeParaElUsuarioSaleDelCodigoNoDelTextoDelFault() {
        assertEquals("Tu obra social informa que tu afiliación está dada de baja.",
                FaultsDelLegado.mensajeParaElUsuario("AFILIADO_DE_BAJA"));
        assertEquals("Tu obra social rechazó la consulta.", FaultsDelLegado.mensajeParaElUsuario("ERR-PAD-017 interno"));
        assertEquals("Tu obra social rechazó la consulta.", FaultsDelLegado.mensajeParaElUsuario(null));
    }

    @Test
    void unRechazoDeNegocioSeCobraComoParticularConElMensajePropio() {
        ResultadoCobertura rechazada = ResultadoCobertura.rechazadaPorLaObraSocial(
                FaultsDelLegado.mensajeParaElUsuario("AFILIADO_DE_BAJA"));

        assertEquals(ar.edu.mediconecta.modelo.EstadoCobertura.PARTICULAR, rechazada.getEstado());
        assertTrue(rechazada.getMensaje().startsWith("Tu obra social informa"));
        assertFalse(rechazada.getMensaje().contains("ERR-PAD"));
        assertFalse(rechazada.isRequiereAutorizacion());
    }
}
