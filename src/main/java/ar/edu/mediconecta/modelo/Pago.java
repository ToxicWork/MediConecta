package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Cobro de la parte a cargo del paciente, procesado por la pasarela externa (REST). Nunca guarda el
 * número de tarjeta: sólo la marca y los últimos 4 dígitos que devuelve la pasarela.
 */
@Entity
@Table(name = "pago")
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    @Column(name = "importe", nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    @Column(name = "moneda", nullable = false, length = 3)
    private String moneda;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPago estado;

    @Column(name = "id_pasarela", nullable = false, unique = true, length = 60)
    private String idPasarela;

    @Column(name = "marca", length = 20)
    private String marca;

    @Column(name = "ultimos4", length = 4)
    private String ultimos4;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "reembolsado_en")
    private LocalDateTime reembolsadoEn;

    @Column(name = "motivo_reembolso", length = 200)
    private String motivoReembolso;

    protected Pago() {
    }

    public Pago(Turno turno, BigDecimal importe, String moneda, String idPasarela, String marca, String ultimos4) {
        this.turno = turno;
        this.pacienteId = turno.getPaciente().getId();
        this.profesionalId = turno.getProfesional().getId();
        this.importe = importe;
        this.moneda = moneda;
        this.idPasarela = idPasarela;
        this.marca = marca;
        this.ultimos4 = ultimos4;
        this.estado = EstadoPago.APROBADO;
        this.creadoEn = LocalDateTime.now();
    }

    public void marcarReembolsado(String motivo) {
        if (estado == EstadoPago.APROBADO) {
            estado = EstadoPago.REEMBOLSADO;
            reembolsadoEn = LocalDateTime.now();
            motivoReembolso = motivo;
        }
    }

    public Long getId() {
        return id;
    }

    public Turno getTurno() {
        return turno;
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public String getMoneda() {
        return moneda;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public String getIdPasarela() {
        return idPasarela;
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
