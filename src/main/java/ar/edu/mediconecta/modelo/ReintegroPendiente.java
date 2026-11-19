package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un reintegro que la pasarela no pudo hacer en el momento y que no se puede perder (D-06 y D-41).
 *
 * <ul>
 *   <li><b>Pendiente</b>: la pasarela no respondió (caída, o el Circuit Breaker abierto). Se reintenta solo, con
 *       espera creciente: 1, 2, 4, 8... minutos, hasta una hora entre intentos. Después de {@value #MAXIMO_DE_INTENTOS}
 *       intentos pasa a conciliación manual.</li>
 *   <li><b>Conciliación manual</b>: la pasarela respondió que no conoce el pago, o lo rechazó: reintentar no lo
 *       arregla. Lo resuelve el administrativo desde Pagos, que lo puede reintentar o marcar como conciliado.</li>
 *   <li><b>Reintegrado</b> o <b>conciliado</b>: terminado.</li>
 * </ul>
 * Antes, en los dos casos el reintegro sólo quedaba en el log.
 */
@Entity
@Table(name = "reintegro_pendiente")
public class ReintegroPendiente {

    public static final int MAXIMO_DE_INTENTOS = 10;
    static final long ESPERA_MAXIMA_MINUTOS = 60;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_pago", nullable = false, length = 60)
    private String idPago;

    @Column(name = "referencia", length = 80)
    private String referencia;

    @Column(name = "turno_id")
    private Long turnoId;

    @Column(name = "importe", precision = 12, scale = 2)
    private BigDecimal importe;

    @Column(name = "motivo", nullable = false, length = 200)
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen", nullable = false, length = 20)
    private OrigenReintegro origen;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25)
    private EstadoReintegro estado;

    @Column(name = "intentos", nullable = false)
    private int intentos;

    @Column(name = "proximo_intento")
    private LocalDateTime proximoIntento;

    @Column(name = "ultimo_error", length = 300)
    private String ultimoError;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "resuelto_en")
    private LocalDateTime resueltoEn;

    @Column(name = "resuelto_por", length = 120)
    private String resueltoPor;

    protected ReintegroPendiente() {
    }

    private ReintegroPendiente(String idPago, String referencia, Long turnoId, BigDecimal importe, String motivo,
                               OrigenReintegro origen, EstadoReintegro estado, String error, LocalDateTime ahora) {
        if (idPago == null || idPago.isBlank()) {
            throw new IllegalArgumentException("El reintegro necesita el id del pago en la pasarela.");
        }
        this.idPago = idPago;
        this.referencia = referencia;
        this.turnoId = turnoId;
        this.importe = importe;
        this.motivo = motivo;
        this.origen = origen;
        this.estado = estado;
        this.ultimoError = recortar(error);
        this.creadoEn = ahora;
        this.intentos = 1; // el intento que acaba de fallar
        this.proximoIntento = estado == EstadoReintegro.PENDIENTE ? ahora.plusMinutes(1) : null;
    }

    /** La pasarela no respondió: se reintenta solo. */
    public static ReintegroPendiente paraReintentar(String idPago, String referencia, Long turnoId, BigDecimal importe,
                                                    String motivo, OrigenReintegro origen, String error, LocalDateTime ahora) {
        return new ReintegroPendiente(idPago, referencia, turnoId, importe, motivo, origen, EstadoReintegro.PENDIENTE, error, ahora);
    }

    /** La pasarela respondió y reintentar no lo arregla (no conoce el pago, o lo rechazó): lo resuelve una persona. */
    public static ReintegroPendiente paraConciliar(String idPago, String referencia, Long turnoId, BigDecimal importe,
                                                   String motivo, OrigenReintegro origen, String error, LocalDateTime ahora) {
        return new ReintegroPendiente(idPago, referencia, turnoId, importe, motivo, origen, EstadoReintegro.CONCILIACION_MANUAL, error, ahora);
    }

    /** Otro intento fallido porque la pasarela no respondió: espera el doble, o pasa a conciliación manual. */
    public void registrarFalla(String error, LocalDateTime ahora) {
        exigirAbierto();
        intentos++;
        ultimoError = recortar(error);
        if (intentos >= MAXIMO_DE_INTENTOS) {
            pasarAConciliacionManual("Sin respuesta de la pasarela después de " + intentos + " intentos: " + error, ahora);
            return;
        }
        estado = EstadoReintegro.PENDIENTE;
        proximoIntento = ahora.plusMinutes(esperaEnMinutos(intentos));
    }

    /** 1, 2, 4, 8... minutos, hasta una hora. */
    static long esperaEnMinutos(int intentos) {
        return Math.min(1L << Math.min(intentos - 1, 10), ESPERA_MAXIMA_MINUTOS);
    }

    public void pasarAConciliacionManual(String motivoDeLaPasarela, LocalDateTime ahora) {
        exigirAbierto();
        estado = EstadoReintegro.CONCILIACION_MANUAL;
        ultimoError = recortar(motivoDeLaPasarela);
        proximoIntento = null;
    }

    public void marcarReintegrado(LocalDateTime ahora) {
        exigirAbierto();
        estado = EstadoReintegro.REINTEGRADO;
        proximoIntento = null;
        resueltoEn = ahora;
        resueltoPor = "pasarela";
    }

    /** El administrativo lo resolvió por fuera (con la pasarela o con el paciente). */
    public void marcarConciliado(String quien, LocalDateTime ahora) {
        exigirAbierto();
        estado = EstadoReintegro.CONCILIADO;
        proximoIntento = null;
        resueltoEn = ahora;
        resueltoPor = quien;
    }

    public boolean estaAbierto() {
        return estado == EstadoReintegro.PENDIENTE || estado == EstadoReintegro.CONCILIACION_MANUAL;
    }

    private void exigirAbierto() {
        if (!estaAbierto()) {
            throw new ErrorDeNegocioException("El reintegro ya está " + estado.getEtiqueta().toLowerCase() + ".");
        }
    }

    private static String recortar(String texto) {
        return texto == null || texto.length() <= 300 ? texto : texto.substring(0, 297) + "...";
    }

    public Long getId() {
        return id;
    }

    public String getIdPago() {
        return idPago;
    }

    public String getReferencia() {
        return referencia;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public BigDecimal getImporte() {
        return importe;
    }

    public String getMotivo() {
        return motivo;
    }

    public OrigenReintegro getOrigen() {
        return origen;
    }

    public EstadoReintegro getEstado() {
        return estado;
    }

    public int getIntentos() {
        return intentos;
    }

    public LocalDateTime getProximoIntento() {
        return proximoIntento;
    }

    public String getUltimoError() {
        return ultimoError;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public LocalDateTime getResueltoEn() {
        return resueltoEn;
    }

    public String getResueltoPor() {
        return resueltoPor;
    }
}
