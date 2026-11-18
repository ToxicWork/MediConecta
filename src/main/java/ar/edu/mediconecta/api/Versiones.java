package ar.edu.mediconecta.api;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * ETag de un turno (su versión del bloqueo optimista) y las precondiciones {@code If-Match} e {@code If-None-Match}
 * (RFC 9110). Funciones puras, para probarlas sin contenedor.
 */
final class Versiones {

    private Versiones() {
    }

    /** ETag fuerte: la versión entre comillas, por ejemplo {@code "7"}. */
    static String etiqueta(int version) {
        return "\"" + version + "\"";
    }

    /**
     * Las versiones que acepta un {@code If-Match}: vacío si no hay precondición (sin cabecera, o {@code *}, que vale
     * para cualquier versión de un recurso que existe). Compara en forma fuerte: una ETag débil ({@code W/"7"}) o que
     * no es nuestra no coincide nunca, así que el conjunto puede quedar vacío y la precondición falla siempre.
     */
    static Optional<Set<Integer>> aceptadasPorIfMatch(String cabecera) {
        if (cabecera == null || cabecera.isBlank() || cabecera.trim().equals("*")) {
            return Optional.empty();
        }
        Set<Integer> versiones = new HashSet<>();
        for (String etiqueta : cabecera.split(",")) {
            numero(etiqueta.trim()).ifPresent(versiones::add);
        }
        return Optional.of(versiones);
    }

    /** {@code If-None-Match}: si alguna ETag (comparación débil) es la de {@code version}, o es {@code *}. */
    static boolean coincideIfNoneMatch(String cabecera, int version) {
        if (cabecera == null || cabecera.isBlank()) {
            return false;
        }
        for (String etiqueta : cabecera.split(",")) {
            String limpia = etiqueta.trim();
            if (limpia.equals("*")) {
                return true;
            }
            if (limpia.startsWith("W/")) {
                limpia = limpia.substring(2);
            }
            if (numero(limpia).filter(n -> n == version).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static Optional<Integer> numero(String etiqueta) {
        if (etiqueta.length() < 3 || !etiqueta.startsWith("\"") || !etiqueta.endsWith("\"")) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(etiqueta.substring(1, etiqueta.length() - 1)));
        } catch (NumberFormatException noEsNuestra) {
            return Optional.empty();
        }
    }
}
