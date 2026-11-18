package ar.edu.mediconecta.integracion.obrasocial;

import jakarta.xml.soap.Detail;
import jakarta.xml.soap.DetailEntry;
import jakarta.xml.soap.SOAPFault;

import javax.xml.namespace.QName;
import java.util.Iterator;

/**
 * Cómo leer un SOAP Fault del legado (Eje C: «respuesta válida, Fault de negocio, Fault técnico»). Un Fault
 * {@code Client}/{@code Sender} es de negocio: el pedido tiene un problema y repetirlo no lo arregla. Cualquier otro
 * ({@code Server}/{@code Receiver}, o uno de WS-Security) es técnico. Al usuario nunca le llega el texto del Fault:
 * se le muestra un mensaje propio según el código que el legado manda en el detalle.
 */
final class FaultsDelLegado {

    static final String ELEMENTO_CODIGO = "codigoError";

    private FaultsDelLegado() {
    }

    static boolean esDeNegocio(QName codigoDelFault) {
        return codigoDelFault != null
                && ("Client".equals(codigoDelFault.getLocalPart()) || "Sender".equals(codigoDelFault.getLocalPart()));
    }

    /** El mensaje para el paciente según el código del error; uno genérico si el código no es conocido. */
    static String mensajeParaElUsuario(String codigo) {
        return switch (codigo == null ? "" : codigo) {
            case "AFILIADO_DE_BAJA" -> "Tu obra social informa que tu afiliación está dada de baja.";
            case "AFILIADO_INEXISTENTE" -> "Tu obra social no te encontró en su padrón.";
            case "PRESTACION_NO_CUBIERTA" -> "Tu plan no cubre esta prestación.";
            default -> "Tu obra social rechazó la consulta.";
        };
    }

    /** El código de error del detalle del Fault ({@code <codigoError>}), o {@code null} si no viene. */
    static String codigoDelDetalle(SOAPFault fault) {
        Detail detalle = fault == null ? null : fault.getDetail();
        if (detalle == null) {
            return null;
        }
        for (Iterator<DetailEntry> entradas = detalle.getDetailEntries(); entradas.hasNext(); ) {
            DetailEntry entrada = entradas.next();
            if (ELEMENTO_CODIGO.equals(entrada.getElementName().getLocalName())) {
                String valor = entrada.getValue();
                return valor == null ? null : valor.trim();
            }
        }
        return null;
    }
}
