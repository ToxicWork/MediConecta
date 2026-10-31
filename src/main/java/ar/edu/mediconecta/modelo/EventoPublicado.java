package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Registro de cada evento publicado en {@code topico.turnos}. Se guarda en la misma transacción
 * que el envío JMS: si hay rollback, desaparecen los dos. Permite que el monitor de eventos
 * muestre qué suscriptores todavía no procesaron un evento (por ejemplo, si uno está caído).
 *
 * <p>Guarda también qué suscripciones debían recibirlo al momento de publicarse (las que existían y cuyo
 * selector lo acepta), así el monitor no marca como pendiente un evento anterior a una suscripción nueva.
 */
@Entity
@Table(name = "evento_publicado")
public class EventoPublicado {

    @Id
    @Column(name = "id_evento", length = 36)
    private String idEvento;

    @Column(name = "tipo_evento", nullable = false, length = 40)
    private String tipoEvento;

    @Column(name = "turno_id", nullable = false)
    private Long turnoId;

    @Column(name = "fecha_hora_turno", nullable = false)
    private LocalDateTime fechaHoraTurno;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    @Column(name = "publicado_en", nullable = false)
    private LocalDateTime publicadoEn;

    /** Suscripciones destinatarias, separadas por comas. Nulo en los eventos de la Entrega Parcial N.º 2. */
    @Column(name = "suscriptores_esperados", length = 120)
    private String suscriptoresEsperados;

    protected EventoPublicado() {
    }

    public EventoPublicado(String idEvento, String tipoEvento, Long turnoId, LocalDateTime fechaHoraTurno,
                           Long pacienteId, Long profesionalId, List<String> suscriptoresEsperados) {
        this.idEvento = idEvento;
        this.tipoEvento = tipoEvento;
        this.turnoId = turnoId;
        this.fechaHoraTurno = fechaHoraTurno;
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
        this.publicadoEn = LocalDateTime.now();
        this.suscriptoresEsperados = String.join(",", suscriptoresEsperados);
    }

    /** Los eventos anteriores a la Entrega Obligatoria N.º 2 sólo tenían notificaciones y auditoría. */
    public boolean esperaA(String suscripcion) {
        List<String> esperados = suscriptoresEsperados == null ? List.of("notificaciones", "auditoria")
                : Arrays.asList(suscriptoresEsperados.split(","));
        return esperados.contains(suscripcion);
    }

    public String getIdEvento() {
        return idEvento;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public LocalDateTime getFechaHoraTurno() {
        return fechaHoraTurno;
    }

    public LocalDateTime getPublicadoEn() {
        return publicadoEn;
    }
}
