package ar.edu.mediconecta.modelo;

import java.time.Duration;

/**
 * Parámetros del bloqueo de cuentas por intentos de ingreso fallidos (prueba de seguridad SEC-W01). La regla
 * está en {@link Usuario#registrarIntentoFallido}; acá sólo los números, para que la pantalla de login y la
 * documentación digan lo mismo que el código.
 */
public final class PoliticaDeBloqueo {

    /** Intentos fallidos seguidos que bloquean la cuenta. */
    public static final int MAX_INTENTOS_FALLIDOS = 5;

    /** Cuánto dura el bloqueo. Un administrativo lo puede levantar antes desde la pantalla Usuarios. */
    public static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private PoliticaDeBloqueo() {
    }
}
