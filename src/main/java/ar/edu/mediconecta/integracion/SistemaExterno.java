package ar.edu.mediconecta.integracion;

public enum SistemaExterno {
    OBRA_SOCIAL("Obra social", "SOAP"),
    PASARELA_PAGOS("Pasarela de pagos", "REST"),
    VIDEOCONSULTA("Videoconsulta", "REST");

    private final String nombre;
    private final String protocolo;

    SistemaExterno(String nombre, String protocolo) {
        this.nombre = nombre;
        this.protocolo = protocolo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getProtocolo() {
        return protocolo;
    }
}
