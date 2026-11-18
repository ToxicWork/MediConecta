package ar.edu.mediconecta.api;

import ar.edu.mediconecta.modelo.PedidoIdempotente;
import ar.edu.mediconecta.negocio.excepciones.ParametroInvalidoException;

import java.util.regex.Pattern;

/** Formato de la cabecera {@code Idempotency-Key}: un UUID, o cualquier texto corto sin espacios. */
final class Idempotencia {

    static final String CABECERA = "Idempotency-Key";
    /** Cabecera de la respuesta que se devuelve guardada (no se volvió a hacer la operación). */
    static final String REPETIDA = "Idempotent-Replayed";

    private static final Pattern FORMATO = Pattern.compile("[A-Za-z0-9._:-]{1," + PedidoIdempotente.LARGO_MAXIMO_CLAVE + "}");

    private Idempotencia() {
    }

    /** La clave lista para usar, o {@code null} si el pedido no trae; con un formato que no sirve, 400. */
    static String validar(String clave) {
        if (clave == null) {
            return null;
        }
        String limpia = clave.trim();
        if (!FORMATO.matcher(limpia).matches()) {
            throw new ParametroInvalidoException("La " + CABECERA + " tiene que tener entre 1 y "
                    + PedidoIdempotente.LARGO_MAXIMO_CLAVE + " caracteres: letras, números, puntos, guiones o dos puntos.");
        }
        return limpia;
    }
}
