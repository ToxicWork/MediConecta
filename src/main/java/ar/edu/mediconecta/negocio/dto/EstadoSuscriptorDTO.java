package ar.edu.mediconecta.negocio.dto;

import java.io.Serializable;

/**
 * Cómo le fue a un suscriptor con un evento: si le corresponde (selector), si ya lo procesó y cuánto tardó
 * desde la publicación.
 */
public class EstadoSuscriptorDTO implements Serializable {

    private final String suscriptor;
    private final boolean aplica;
    private final boolean procesado;
    private final Long latenciaMs;

    public EstadoSuscriptorDTO(String suscriptor, boolean aplica, boolean procesado, Long latenciaMs) {
        this.suscriptor = suscriptor;
        this.aplica = aplica;
        this.procesado = procesado;
        this.latenciaMs = latenciaMs;
    }

    public String getSuscriptor() {
        return suscriptor;
    }

    public boolean isAplica() {
        return aplica;
    }

    public boolean isProcesado() {
        return procesado;
    }

    public boolean isPendiente() {
        return aplica && !procesado;
    }

    public Long getLatenciaMs() {
        return latenciaMs;
    }
}
