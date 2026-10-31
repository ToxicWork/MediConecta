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
import jakarta.persistence.Version;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Turno de la agenda de un profesional. Las transiciones de estado se delegan en
 * {@link EstadoTurno#comportamiento()} (patrón State); los mutadores que usan los estados son
 * de paquete para que nadie fuera del modelo pueda saltearse las reglas.
 *
 * <p>Las columnas agregadas en la Entrega Obligatoria N.º 2 son opcionales en la base para no
 * romper los turnos existentes ({@code hbm2ddl.auto=update}); los getters aplican los valores por defecto.
 */
@Entity
@Table(name = "turno")
public class Turno implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "profesional_id", nullable = false)
    private Profesional profesional;

    @ManyToOne
    @JoinColumn(name = "paciente_id")
    private Paciente paciente;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoTurno estado;

    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", length = 20)
    private Modalidad modalidad;

    @Column(name = "hold_vence_en")
    private LocalDateTime holdVenceEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "cobertura", length = 20)
    private EstadoCobertura cobertura;

    @Column(name = "obra_social_turno", length = 120)
    private String obraSocial;

    @Column(name = "plan_obra_social", length = 40)
    private String plan;

    @Column(name = "codigo_prestacion", length = 20)
    private String codigoPrestacion;

    @Column(name = "importe_total", precision = 12, scale = 2)
    private BigDecimal importeTotal;

    @Column(name = "importe_paciente", precision = 12, scale = 2)
    private BigDecimal importePaciente;

    @Column(name = "importe_obra_social", precision = 12, scale = 2)
    private BigDecimal importeObraSocial;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_autorizacion", length = 20)
    private EstadoAutorizacion estadoAutorizacion;

    @Column(name = "codigo_autorizacion", length = 40)
    private String codigoAutorizacion;

    @Column(name = "motivo_autorizacion", length = 300)
    private String motivoAutorizacion;

    @Column(name = "sala_id", length = 60)
    private String salaId;

    @Column(name = "sala_url_profesional", length = 300)
    private String salaUrlProfesional;

    @Column(name = "sala_url_paciente", length = 300)
    private String salaUrlPaciente;

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;

    @Column(name = "cancelado_en")
    private LocalDateTime canceladoEn;

    @Version
    private int version;

    protected Turno() {
    }

    public Turno(Profesional profesional, LocalDateTime fechaHora, Modalidad modalidad) {
        this.profesional = profesional;
        this.fechaHora = fechaHora;
        this.modalidad = modalidad == null ? Modalidad.PRESENCIAL : modalidad;
        this.estado = EstadoTurno.DISPONIBLE;
    }

    public Turno(Profesional profesional, LocalDateTime fechaHora) {
        this(profesional, fechaHora, Modalidad.PRESENCIAL);
    }

    // ---- Transiciones (patrón State) ---------------------------------------------------------

    public void reservar(Paciente paciente, LocalDateTime venceEn) {
        estado = estado.comportamiento().reservar(this, paciente, venceEn);
    }

    public void confirmar(DatosConfirmacion datos) {
        estado = estado.comportamiento().confirmar(this, datos);
    }

    public void cancelar() {
        estado = estado.comportamiento().cancelar(this);
    }

    public void liberar() {
        estado = estado.comportamiento().liberar(this);
    }

    /** Al cancelar, el horario vuelve a ofrecerse como un turno nuevo; el cancelado queda como historial. */
    public Turno horarioLiberado() {
        return new Turno(profesional, fechaHora, getModalidad());
    }

    // ---- Mutadores que usan los estados concretos --------------------------------------------

    void asignarPaciente(Paciente paciente, LocalDateTime venceEn) {
        this.paciente = paciente;
        this.holdVenceEn = venceEn;
    }

    void quitarPaciente() {
        this.paciente = null;
        this.holdVenceEn = null;
    }

    void aplicarConfirmacion(DatosConfirmacion datos) {
        this.cobertura = datos.cobertura();
        this.obraSocial = datos.obraSocial();
        this.plan = datos.plan();
        this.codigoPrestacion = datos.codigoPrestacion();
        this.importeTotal = datos.importeTotal();
        this.importePaciente = datos.importePaciente();
        this.importeObraSocial = datos.importeObraSocial();
        this.estadoAutorizacion = datos.autorizacion();
        this.holdVenceEn = null;
        this.confirmadoEn = LocalDateTime.now();
    }

    void marcarCancelado() {
        this.canceladoEn = LocalDateTime.now();
    }

    // ---- Datos que llegan de las integraciones -----------------------------------------------

    /** Respuesta asincrónica de la obra social (cola de autorizaciones). */
    public void registrarAutorizacion(boolean autorizada, String codigo, String motivo) {
        if (estado != EstadoTurno.CONFIRMADO) {
            throw new TransicionInvalidaException("Sólo se registra la autorización de un turno confirmado.");
        }
        this.estadoAutorizacion = autorizada ? EstadoAutorizacion.AUTORIZADA : EstadoAutorizacion.RECHAZADA;
        this.codigoAutorizacion = codigo;
        this.motivoAutorizacion = motivo;
        if (autorizada && cobertura == EstadoCobertura.PENDIENTE) {
            this.cobertura = EstadoCobertura.CUBIERTA;
        }
    }

    public void asignarSala(String salaId, String urlProfesional, String urlPaciente) {
        if (estado != EstadoTurno.CONFIRMADO || getModalidad() != Modalidad.TELEMEDICINA) {
            throw new TransicionInvalidaException("Sólo un turno de telemedicina confirmado tiene sala de videoconsulta.");
        }
        this.salaId = salaId;
        this.salaUrlProfesional = urlProfesional;
        this.salaUrlPaciente = urlPaciente;
    }

    // ---- Consultas ---------------------------------------------------------------------------

    public boolean perteneceAPaciente(Long pacienteId) {
        return paciente != null && paciente.getId().equals(pacienteId);
    }

    public boolean esDelProfesional(Long profesionalId) {
        return profesional.getId().equals(profesionalId);
    }

    public boolean isTelemedicina() {
        return getModalidad() == Modalidad.TELEMEDICINA;
    }

    public Long getId() {
        return id;
    }

    public Profesional getProfesional() {
        return profesional;
    }

    public Paciente getPaciente() {
        return paciente;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public EstadoTurno getEstado() {
        return estado;
    }

    public Modalidad getModalidad() {
        return modalidad == null ? Modalidad.PRESENCIAL : modalidad;
    }

    public LocalDateTime getHoldVenceEn() {
        return holdVenceEn;
    }

    public EstadoCobertura getCobertura() {
        return cobertura;
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public String getPlan() {
        return plan;
    }

    public String getCodigoPrestacion() {
        return codigoPrestacion;
    }

    public BigDecimal getImporteTotal() {
        return importeTotal;
    }

    public BigDecimal getImportePaciente() {
        return importePaciente;
    }

    public BigDecimal getImporteObraSocial() {
        return importeObraSocial;
    }

    public EstadoAutorizacion getEstadoAutorizacion() {
        return estadoAutorizacion == null ? EstadoAutorizacion.NO_REQUIERE : estadoAutorizacion;
    }

    public String getCodigoAutorizacion() {
        return codigoAutorizacion;
    }

    public String getMotivoAutorizacion() {
        return motivoAutorizacion;
    }

    public String getSalaId() {
        return salaId;
    }

    public String getSalaUrlProfesional() {
        return salaUrlProfesional;
    }

    public String getSalaUrlPaciente() {
        return salaUrlPaciente;
    }

    public LocalDateTime getConfirmadoEn() {
        return confirmadoEn;
    }

    public LocalDateTime getCanceladoEn() {
        return canceladoEn;
    }
}
