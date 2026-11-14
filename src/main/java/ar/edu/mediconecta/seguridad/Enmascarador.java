package ar.edu.mediconecta.seguridad;

/**
 * Enmascara datos personales antes de escribirlos en el log (prueba de seguridad SEC-D02). El log lo leen
 * personas de sistemas y queda en archivos que se copian y rotan: alcanza con poder reconocer a quién se refiere
 * una línea, no hace falta el dato completo. Para identificar a alguien sin ambigüedad, el log usa ids.
 */
public final class Enmascarador {

    private static final String MASCARA = "***";

    private Enmascarador() {
    }

    /**
     * {@code juan.perez@mediconecta.test} → {@code ju***@mediconecta.test}. Se conserva el dominio (sirve para
     * diagnosticar problemas de entrega) y, del usuario, sólo los dos primeros caracteres (uno si es muy corto).
     */
    public static String email(String email) {
        if (email == null) {
            return MASCARA;
        }
        int arroba = email.lastIndexOf('@');
        if (arroba <= 0) {
            return MASCARA;
        }
        String usuario = email.substring(0, arroba);
        int visibles = usuario.length() > 4 ? 2 : 1;
        return usuario.substring(0, visibles) + MASCARA + email.substring(arroba);
    }
}
