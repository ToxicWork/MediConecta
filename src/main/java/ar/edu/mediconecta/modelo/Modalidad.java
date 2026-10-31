package ar.edu.mediconecta.modelo;

public enum Modalidad {
    PRESENCIAL("Presencial"),
    TELEMEDICINA("Telemedicina");

    private final String etiqueta;

    Modalidad(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
