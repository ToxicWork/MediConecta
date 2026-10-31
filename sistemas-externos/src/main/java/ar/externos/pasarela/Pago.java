package ar.externos.pasarela;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Estado de un pago en la pasarela. Nunca guarda el número completo de la tarjeta: sólo los últimos 4. */
public class Pago {

    public enum Estado { APROBADO, RECHAZADO, REEMBOLSADO }

    private String id;
    private Estado estado;
    private String motivoRechazo;
    private BigDecimal importe;
    private String moneda;
    private String referencia;
    private String descripcion;
    private String marca;
    private String ultimos4;
    private LocalDateTime creadoEn;
    private LocalDateTime reembolsadoEn;
    private String motivoReembolso;

    public Pago() {
    }

    Pago(String id, Estado estado, String motivoRechazo, BigDecimal importe, String moneda, String referencia,
         String descripcion, String marca, String ultimos4) {
        this.id = id;
        this.estado = estado;
        this.motivoRechazo = motivoRechazo;
        this.importe = importe;
        this.moneda = moneda;
        this.referencia = referencia;
        this.descripcion = descripcion;
        this.marca = marca;
        this.ultimos4 = ultimos4;
        this.creadoEn = LocalDateTime.now();
    }

    void reembolsar(String motivo) {
        this.estado = Estado.REEMBOLSADO;
        this.reembolsadoEn = LocalDateTime.now();
        this.motivoReembolso = motivo;
    }

    public String getId() {
        return id;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public String getMoneda() {
        return moneda;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getMarca() {
        return marca;
    }

    public String getUltimos4() {
        return ultimos4;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getReembolsadoEn() {
        return reembolsadoEn;
    }

    public String getMotivoReembolso() {
        return motivoReembolso;
    }
}
