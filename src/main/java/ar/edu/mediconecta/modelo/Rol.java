package ar.edu.mediconecta.modelo;

public enum Rol {
    PACIENTE,
    PROFESIONAL,
    /** Personal administrativo: por ahora sólo accede a Pagos (cobros y reclamos a obras sociales). */
    ADMINISTRATIVO;

    /** Nombre para mostrar en la interfaz (la seguridad declarativa usa {@link #name()}). */
    public String getEtiqueta() {
        return switch (this) {
            case PACIENTE -> "Paciente";
            case PROFESIONAL -> "Profesional";
            case ADMINISTRATIVO -> "Administrativo";
        };
    }
}
