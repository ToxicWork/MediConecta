package ar.edu.mediconecta.mensajeria;

import java.util.List;

/** Nombres de las suscripciones durables de {@code topico.turnos}. */
public final class Suscripciones {

    public static final String NOTIFICACIONES = "notificaciones";
    public static final String AUDITORIA = "auditoria";

    public static final List<String> TODAS = List.of(NOTIFICACIONES, AUDITORIA);

    private Suscripciones() {
    }
}
