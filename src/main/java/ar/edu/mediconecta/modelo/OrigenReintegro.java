package ar.edu.mediconecta.modelo;

/** Por qué hay que devolver el dinero: la confirmación se revirtió después de cobrar, o el turno se canceló. */
public enum OrigenReintegro {
    COMPENSACION("Confirmación revertida"),
    CANCELACION("Turno cancelado");

    private final String etiqueta;

    OrigenReintegro(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
