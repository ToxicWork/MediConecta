package ar.edu.mediconecta.integracion.obrasocial;

import ar.edu.mediconecta.integracion.resiliencia.RespuestaDeNegocio;

/**
 * El legado respondió con un SOAP Fault de negocio ({@code Client} en SOAP 1.1, {@code Sender} en 1.2): funciona,
 * pero rechaza el pedido por sus datos, por ejemplo un afiliado dado de baja. No es una falla de disponibilidad: no se
 * reintenta, para el circuito es una respuesta (lo cierra si estaba probando) y el usuario recibe un mensaje propio,
 * nunca el texto del Fault.
 */
public class RechazoDelLegadoException extends RuntimeException implements RespuestaDeNegocio {

    private final String codigo;

    public RechazoDelLegadoException(String codigo, String descripcion, Throwable causa) {
        super("SOAP Fault de negocio " + codigo + ": " + descripcion, causa);
        this.codigo = codigo;
    }

    /** El código de error que el legado manda en el detalle del Fault, o {@code null} si no manda. */
    public String getCodigo() {
        return codigo;
    }
}
