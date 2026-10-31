package ar.edu.mediconecta.modelo;

public enum EstadoPago {
    APROBADO("Aprobado"),
    REEMBOLSADO("Reembolsado");

    private final String etiqueta;

    EstadoPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
