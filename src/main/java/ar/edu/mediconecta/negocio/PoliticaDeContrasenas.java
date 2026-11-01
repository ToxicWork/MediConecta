package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.negocio.excepciones.DatosDeUsuarioInvalidosException;

/**
 * Reglas de las contraseñas, en un solo lugar para el registro, el alta por un administrativo y el cambio
 * desde "Mi perfil". Clase pura (sin contenedor) para poder probarla con pruebas unitarias.
 */
public final class PoliticaDeContrasenas {

    public static final int LONGITUD_MINIMA = 8;
    public static final int LONGITUD_MAXIMA = 64;

    private PoliticaDeContrasenas() {
    }

    public static void validar(String password) {
        if (password == null || password.isBlank()) {
            throw new DatosDeUsuarioInvalidosException("Elegí una contraseña.");
        }
        if (password.length() < LONGITUD_MINIMA) {
            throw new DatosDeUsuarioInvalidosException(
                    "La contraseña tiene que tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        if (password.length() > LONGITUD_MAXIMA) {
            throw new DatosDeUsuarioInvalidosException(
                    "La contraseña puede tener hasta " + LONGITUD_MAXIMA + " caracteres.");
        }
        if (!password.equals(password.strip())) {
            throw new DatosDeUsuarioInvalidosException("La contraseña no puede empezar ni terminar con espacios.");
        }
    }

    /** Además de las reglas generales, la nueva tiene que coincidir con la repetición y no ser la misma de antes. */
    public static void validarCambio(String actual, String nueva, String repeticion) {
        validar(nueva);
        if (!nueva.equals(repeticion)) {
            throw new DatosDeUsuarioInvalidosException("La contraseña nueva y su repetición no coinciden.");
        }
        if (nueva.equals(actual)) {
            throw new DatosDeUsuarioInvalidosException("La contraseña nueva tiene que ser distinta de la actual.");
        }
    }
}
