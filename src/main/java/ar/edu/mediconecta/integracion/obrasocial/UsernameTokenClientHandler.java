package ar.edu.mediconecta.integracion.obrasocial;

import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPEnvelope;
import jakarta.xml.soap.SOAPException;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;

import javax.xml.namespace.QName;
import java.util.Set;

/**
 * Handler JAX-WS del lado cliente: agrega a cada pedido el encabezado WS-Security
 * {@code wsse:Security/wsse:UsernameToken} con las credenciales de prestador que la obra social le
 * asignó a MediConecta. El legado lo valida antes de ejecutar la operación.
 */
public class UsernameTokenClientHandler implements SOAPHandler<SOAPMessageContext> {

    private static final String WSSE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";

    private final String usuario;
    private final String clave;

    public UsernameTokenClientHandler(String usuario, String clave) {
        this.usuario = usuario;
        this.clave = clave;
    }

    @Override
    public boolean handleMessage(SOAPMessageContext contexto) {
        if (!Boolean.TRUE.equals(contexto.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY))) {
            return true;
        }
        try {
            SOAPEnvelope sobre = contexto.getMessage().getSOAPPart().getEnvelope();
            SOAPHeader header = sobre.getHeader() == null ? sobre.addHeader() : sobre.getHeader();
            SOAPElement seguridad = header.addChildElement("Security", "wsse", WSSE);
            SOAPElement token = seguridad.addChildElement("UsernameToken", "wsse");
            token.addChildElement("Username", "wsse").addTextNode(usuario);
            token.addChildElement("Password", "wsse").addTextNode(clave);
            contexto.getMessage().saveChanges();
            return true;
        } catch (SOAPException e) {
            throw new IllegalStateException("No se pudo armar el encabezado WS-Security", e);
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
        return Set.of();
    }
}
