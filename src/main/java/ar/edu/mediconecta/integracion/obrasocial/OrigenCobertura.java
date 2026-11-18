package ar.edu.mediconecta.integracion.obrasocial;

/** De dónde salió la respuesta de cobertura, para explicarle al paciente qué pasó. */
public enum OrigenCobertura {
    LEGADO("Consultada en línea a tu obra social"),
    CACHE("Tu obra social no respondió: usamos tu última consulta vigente"),
    SIN_RESPUESTA("Tu obra social no responde: la cobertura se completa cuando conteste"),
    SIN_OBRA_SOCIAL("Sin obra social declarada: atención particular");

    private final String descripcion;

    OrigenCobertura(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
