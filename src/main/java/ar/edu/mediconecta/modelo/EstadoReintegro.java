package ar.edu.mediconecta.modelo;

/** Estado de un reintegro que la pasarela no pudo hacer en el momento (ver {@link ReintegroPendiente}). */
public enum EstadoReintegro {
    PENDIENTE("Pendiente: se reintenta solo"),
    CONCILIACION_MANUAL("Conciliación manual"),
    REINTEGRADO("Reintegrado"),
    CONCILIADO("Conciliado a mano");

    private final String etiqueta;

    EstadoReintegro(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
