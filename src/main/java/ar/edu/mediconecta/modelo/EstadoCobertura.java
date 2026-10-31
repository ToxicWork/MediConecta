package ar.edu.mediconecta.modelo;

/**
 * Cobertura con la que quedó confirmado un turno.
 * <ul>
 *   <li>CUBIERTA: la obra social (sistema legado) confirmó la cobertura, en línea o desde la caché.</li>
 *   <li>PARTICULAR: el paciente no tiene obra social o no está cubierto: paga el total.</li>
 *   <li>PENDIENTE: el legado no respondió y no había caché; se resuelve por la cola de autorizaciones.</li>
 * </ul>
 */
public enum EstadoCobertura {
    CUBIERTA("Cubierta"),
    PARTICULAR("Particular"),
    PENDIENTE("Pendiente");

    private final String etiqueta;

    EstadoCobertura(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
