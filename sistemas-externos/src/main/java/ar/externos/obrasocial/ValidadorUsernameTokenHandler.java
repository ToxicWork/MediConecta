package ar.externos.obrasocial;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;
import jakarta.xml.ws.soap.SOAPFaultException;

import javax.xml.namespace.QName;
import java.util.Iterator;
import java.util.Set;

/**
 * WS-Security, perfil UsernameToken (OASIS): el legado sólo atiende a prestadores registrados. Lee el
 * encabezado {@code wsse:Security/wsse:UsernameToken} y, si falta o no coincide, responde un SOAP Fault
 * {@code wsse:FailedAuthentication} sin llegar a ejecutar la operación.
 */
public class ValidadorUsernameTokenHandler implements SOAPHandler<SOAPMessageContext> {

    static final String WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    private static final QName SECURITY = new QName(WSSE, "Security");

    /** Credenciales que la obra social le asignó a MediConecta como prestador (datos de prueba). */
    private static final String USUARIO = "mediconecta";
    private static final String CLAVE = "prestador-2026";

    @Override
    public boolean handleMessage(SOAPMessageContext contexto) {
        boolean saliente = Boolean.TRUE.equals(contexto.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY));
        if (saliente) {
            return true;
        }
        try {
            SOAPHeader header = contexto.getMessage().getSOAPHeader();
            SOAPElement token = header == null ? null : hijo(hijo(header, "Security"), "UsernameToken");
            String usuario = texto(hijo(token, "Username"));
            String clave = texto(hijo(token, "Password"));
            if (!USUARIO.equals(usuario) || !CLAVE.equals(clave)) {
                throw falla("Credenciales del prestador inválidas o ausentes (wsse:UsernameToken).");
            }
            return true;
        } catch (SOAPException e) {
            throw falla("Encabezado de seguridad ilegible.");
        }
    }

    private static SOAPElement hijo(SOAPElement padre, String nombre) {
        if (padre == null) {
            return null;
        }
        Iterator<?> hijos = padre.getChildElements(new QName(WSSE, nombre));
        return hijos.hasNext() ? (SOAPElement) hijos.next() : null;
    }

    private static String texto(SOAPElement elemento) {
        return elemento == null ? null : elemento.getValue();
    }

    private static SOAPFaultException falla(String mensaje) {
        try {
            SOAPFault fault = SOAPFactory.newInstance().createFault(mensaje,
                    new QName(WSSE, "FailedAuthentication", "wsse"));
            return new SOAPFaultException(fault);
        } catch (SOAPException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean handleFault(SOAPMessageContext contexto) {
        return true;
    }

    @Override
    public void close(MessageContext contexto) {
    }

    @Override
    public Set<QName> getHeaders() {
        return Set.of(SECURITY);
    }
}
