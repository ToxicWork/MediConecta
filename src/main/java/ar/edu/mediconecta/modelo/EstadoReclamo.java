package ar.edu.mediconecta.modelo;

public enum EstadoReclamo {
    PENDIENTE_PRESENTACION("Pendiente de presentación"),
    ANULADO("Anulado");

    private final String etiqueta;

    EstadoReclamo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
