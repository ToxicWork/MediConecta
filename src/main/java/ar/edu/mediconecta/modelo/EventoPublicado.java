package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro de cada evento publicado en {@code topico.turnos}. Se guarda en la misma transacción
 * que el envío JMS: si hay rollback, desaparecen los dos. Permite que el monitor de eventos
 * muestre qué suscriptores todavía no procesaron un evento (por ejemplo, si uno está caído).
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

    protected EventoPublicado() {
    }

    public EventoPublicado(String idEvento, String tipoEvento, Long turnoId, LocalDateTime fechaHoraTurno,
                           Long pacienteId, Long profesionalId) {
        this.idEvento = idEvento;
        this.tipoEvento = tipoEvento;
        this.turnoId = turnoId;
        this.fechaHoraTurno = fechaHoraTurno;
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
        this.publicadoEn = LocalDateTime.now();
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
