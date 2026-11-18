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

    /** Cuándo el profesional cerró la sala al terminar la videoconsulta (D-14); null mientras esté abierta o sin abrir. */
    @Column(name = "sala_cerrada_en")
    private LocalDateTime salaCerradaEn;

    @Column(name = "confirmado_en")
    private LocalDateTime confirmadoEn;

    @Column(name = "cancelado_en")
    private LocalDateTime canceladoEn;

    /** Quién canceló el turno: el paciente, su profesional o un administrativo. {@code null} si no se canceló. */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancelado_por", length = 20)
    private Rol canceladoPor;

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

    /**
     * Cancela y registra quién lo hizo. Lo decide el patrón State: sólo un turno confirmado que todavía no empezó;
     * cualquier otro caso lanza {@link TransicionInvalidaException} (409 en la API). {@code ahora} lo pasa quien
     * cancela: el reloj del servidor en producción, uno fijo en las pruebas.
     */
    public void cancelar(Rol quien, LocalDateTime ahora) {
        estado = estado.comportamiento().cancelar(this, ahora);
        this.canceladoPor = quien;
    }

    /** Si en este momento se puede cancelar (misma regla que {@link #cancelar}): la vista ofrece el botón sólo entonces. */
    public boolean admiteCancelacion(LocalDateTime ahora) {
        return estado.comportamiento().admiteCancelacion(this, ahora);
    }

    /**
     * Si al cancelar el horario se vuelve a ofrecer a otros pacientes: sólo cuando cancela el propio paciente y el
     * turno todavía no pasó. Si cancela el profesional o un administrativo, normalmente es porque el profesional no
     * puede atender en ese horario, así que no se vuelve a publicar.
     */
    public boolean liberaElHorario(LocalDateTime ahora) {
        return canceladoPor == Rol.PACIENTE && fechaHora.isAfter(ahora);
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

    void marcarCancelado(LocalDateTime ahora) {
        this.canceladoEn = ahora;
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

    /**
     * El profesional termina la videoconsulta: la sala se cierra y el turno sale de la lista de Telemedicina (D-14).
     * No cambia el estado del turno (sigue confirmado): la sala es un recurso del proveedor de video, no del turno.
     */
    public void cerrarSala(LocalDateTime ahora) {
        if (salaId == null) {
            throw new TransicionInvalidaException("La videoconsulta de este turno no se inició.");
        }
        if (salaCerradaEn == null) {
            salaCerradaEn = ahora;
        }
    }

    public boolean isSalaAbierta() {
        return salaId != null && salaCerradaEn == null;
    }

    public LocalDateTime getSalaCerradaEn() {
        return salaCerradaEn;
    }

    public boolean perteneceAPaciente(Long pacienteId) {
        return paciente != null && paciente.getId().equals(pacienteId);
    }

    public boolean esDelProfesional(Long profesionalId) {
        return profesional.getId().equals(profesionalId);
    }

    /**
     * SEC-D01: una entrada de historia clínica sólo se puede asociar a un turno confirmado entre ese profesional y
     * ese paciente (no a un turno ajeno, ni cancelado, ni todavía en espera).
     */
    public boolean habilitaHistoriaClinica(Long profesionalId, Long pacienteId) {
        return getEstado() == EstadoTurno.CONFIRMADO && esDelProfesional(profesionalId) && perteneceAPaciente(pacienteId);
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

    public Rol getCanceladoPor() {
        return canceladoPor;
    }

    public LocalDateTime getCanceladoEn() {
        return canceladoEn;
    }
}
