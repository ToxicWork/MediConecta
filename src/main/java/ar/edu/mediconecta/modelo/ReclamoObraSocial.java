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
 * ServicioDeFacturacion: importe que el profesional le reclama a la obra social por una prestación
 * autorizada. Lo genera {@code FacturacionSubscriber} al consumir {@code TURNO_AUTORIZADO}.
 */
@Entity
@Table(name = "reclamo_obra_social")
public class ReclamoObraSocial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    @Column(name = "obra_social", nullable = false, length = 120)
    private String obraSocial;

    @Column(name = "codigo_prestacion", length = 20)
    private String codigoPrestacion;

    @Column(name = "codigo_autorizacion", length = 40)
    private String codigoAutorizacion;

    @Column(name = "importe", nullable = false, precision = 12, scale = 2)
    private BigDecimal importe;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoReclamo estado;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "anulado_en")
    private LocalDateTime anuladoEn;

    protected ReclamoObraSocial() {
    }

    public ReclamoObraSocial(Turno turno, BigDecimal importe) {
        this.turno = turno;
        this.profesionalId = turno.getProfesional().getId();
        this.obraSocial = turno.getObraSocial();
        this.codigoPrestacion = turno.getCodigoPrestacion();
        this.codigoAutorizacion = turno.getCodigoAutorizacion();
        this.importe = importe;
        this.estado = EstadoReclamo.PENDIENTE_PRESENTACION;
        this.creadoEn = LocalDateTime.now();
    }

    public void anular() {
        estado = EstadoReclamo.ANULADO;
        anuladoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Turno getTurno() {
        return turno;
    }

    public Long getProfesionalId() {
        return profesionalId;
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public String getCodigoPrestacion() {
        return codigoPrestacion;
    }

    public String getCodigoAutorizacion() {
        return codigoAutorizacion;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public EstadoReclamo getEstado() {
        return estado;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getAnuladoEn() {
        return anuladoEn;
    }
}
