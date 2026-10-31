package ar.edu.mediconecta.integracion.obrasocial;

/** De dónde salió la respuesta de cobertura, para explicarle al paciente qué pasó. */
public enum OrigenCobertura {
    LEGADO("Consultada en línea al sistema de la obra social (SOAP)"),
    CACHE("El sistema de la obra social no respondió: se usó la última validación vigente (caché)"),
    SIN_RESPUESTA("El sistema de la obra social no responde: la cobertura se resolverá más tarde"),
    SIN_OBRA_SOCIAL("Sin obra social declarada: atención particular");

    private final String descripcion;

    OrigenCobertura(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
