package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Idempotent Receiver: una fila por (suscriptor, idEvento). La clave primaria impide que un
 * mismo suscriptor procese dos veces un evento que el broker entregó más de una vez.
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @Id
    @Column(name = "clave", length = 80)
    private String clave;

    @Column(name = "procesado_en", nullable = false)
    private LocalDateTime procesadoEn;

    protected EventoProcesado() {
    }

    public EventoProcesado(String suscriptor, String idEvento) {
        this.clave = clave(suscriptor, idEvento);
        this.procesadoEn = LocalDateTime.now();
    }

    public static String clave(String suscriptor, String idEvento) {
        return suscriptor + ":" + idEvento;
    }

    public String getClave() {
        return clave;
    }

    public LocalDateTime getProcesadoEn() {
        return procesadoEn;
    }
}
