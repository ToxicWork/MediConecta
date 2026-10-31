package ar.edu.mediconecta.modelo;

public enum EstadoSolicitud {
    PENDIENTE("En cola"),
    AUTORIZADA("Autorizada"),
    RECHAZADA("Rechazada"),
    ANULADA("Anulada");

    private final String etiqueta;

    EstadoSolicitud(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
