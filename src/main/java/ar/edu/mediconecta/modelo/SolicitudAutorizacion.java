package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Solicitud de autorización de una prestación a la obra social. Se crea en la misma transacción que
 * confirma el turno y que envía el mensaje a {@code cola.autorizaciones}; la resuelve
 * {@code AutorizacionConsumer} cuando el sistema legado responde.
 */
@Entity
@Table(name = "solicitud_autorizacion")
public class SolicitudAutorizacion {

    @Id
    @Column(name = "id_solicitud", length = 36)
    private String idSolicitud;

    @ManyToOne(optional = false)
    @JoinColumn(name = "turno_id", nullable = false)
    private Turno turno;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    @Column(name = "obra_social", nullable = false, length = 120)
    private String obraSocial;

    @Column(name = "codigo_prestacion", nullable = false, length = 20)
    private String codigoPrestacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoSolicitud estado;

    @Column(name = "codigo_autorizacion", length = 40)
    private String codigoAutorizacion;

    @Column(name = "motivo", length = 300)
    private String motivo;

    @Column(name = "intento")
    private Integer intento;

    @Column(name = "procesada_por", length = 120)
    private String procesadaPor;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "resuelta_en")
    private LocalDateTime resueltaEn;

    protected SolicitudAutorizacion() {
    }

    public SolicitudAutorizacion(Turno turno, String obraSocial, String codigoPrestacion) {
        this.idSolicitud = UUID.randomUUID().toString();
        this.turno = turno;
        this.pacienteId = turno.getPaciente().getId();
        this.profesionalId = turno.getProfesional().getId();
        this.obraSocial = obraSocial;
        this.codigoPrestacion = codigoPrestacion;
        this.estado = EstadoSolicitud.PENDIENTE;
        this.creadaEn = LocalDateTime.now();
    }

    public void resolver(boolean autorizada, String codigo, String motivo, int intento, String procesadaPor) {
        this.estado = autorizada ? EstadoSolicitud.AUTORIZADA : EstadoSolicitud.RECHAZADA;
        this.codigoAutorizacion = codigo;
        this.motivo = motivo;
        this.intento = intento;
        this.procesadaPor = procesadaPor;
        this.resueltaEn = LocalDateTime.now();
    }

    public void anular(String motivo, int intento, String procesadaPor) {
        this.estado = EstadoSolicitud.ANULADA;
        this.motivo = motivo;
        this.intento = intento;
        this.procesadaPor = procesadaPor;
        this.resueltaEn = LocalDateTime.now();
    }

    public String getIdSolicitud() {
        return idSolicitud;
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

    public String getObraSocial() {
        return obraSocial;
    }

    public String getCodigoPrestacion() {
        return codigoPrestacion;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public String getCodigoAutorizacion() {
        return codigoAutorizacion;
    }

    public String getMotivo() {
        return motivo;
    }

    public Integer getIntento() {
        return intento;
    }

    public String getProcesadaPor() {
        return procesadaPor;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public LocalDateTime getResueltaEn() {
        return resueltaEn;
    }
}
