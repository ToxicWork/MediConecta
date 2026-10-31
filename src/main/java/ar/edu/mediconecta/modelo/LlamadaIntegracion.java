package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro de cada llamada a un sistema externo (SOAP o REST). Se escribe en una transacción propia
 * (REQUIRES_NEW): queda aunque la transacción de negocio que hizo la llamada termine en rollback, que es
 * justamente lo que hace falta para ver un cobro y su reembolso compensatorio.
 */
@Entity
@Table(name = "llamada_integracion")
public class LlamadaIntegracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sistema", nullable = false, length = 40)
    private String sistema;

    @Column(name = "operacion", nullable = false, length = 80)
    private String operacion;

    @Column(name = "referencia", length = 80)
    private String referencia;

    @Column(name = "resultado", nullable = false, length = 30)
    private String resultado;

    @Column(name = "detalle", length = 400)
    private String detalle;

    @Column(name = "latencia_ms", nullable = false)
    private long latenciaMs;

    @Column(name = "ocurrida_en", nullable = false)
    private LocalDateTime ocurridaEn;

    protected LlamadaIntegracion() {
    }

    public LlamadaIntegracion(String sistema, String operacion, String referencia, String resultado,
                              String detalle, long latenciaMs) {
        this.sistema = sistema;
        this.operacion = operacion;
        this.referencia = referencia;
        this.resultado = resultado;
        this.detalle = detalle == null || detalle.length() <= 400 ? detalle : detalle.substring(0, 400);
        this.latenciaMs = latenciaMs;
        this.ocurridaEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSistema() {
        return sistema;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getResultado() {
        return resultado;
    }

    public String getDetalle() {
        return detalle;
    }

    public long getLatenciaMs() {
        return latenciaMs;
    }

    public LocalDateTime getOcurridaEn() {
        return ocurridaEn;
    }
}
