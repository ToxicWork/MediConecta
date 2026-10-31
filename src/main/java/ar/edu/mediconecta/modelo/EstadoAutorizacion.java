package ar.edu.mediconecta.modelo;

/** Autorización de la prestación por la obra social, que se resuelve de forma asincrónica (cola). */
public enum EstadoAutorizacion {
    NO_REQUIERE("No requiere"),
    PENDIENTE("Pendiente"),
    AUTORIZADA("Autorizada"),
    RECHAZADA("Rechazada");

    private final String etiqueta;

    EstadoAutorizacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
