package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Traza de cada evento de turno: guarda identificadores, nunca datos clínicos. */
@Entity
@Table(name = "registro_auditoria")
public class RegistroAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_evento", nullable = false, length = 36)
    private String idEvento;

    @Column(name = "tipo_evento", nullable = false, length = 40)
    private String tipoEvento;

    @Column(name = "turno_id", nullable = false)
    private Long turnoId;

    @Column(name = "paciente_id", nullable = false)
    private Long pacienteId;

    @Column(name = "profesional_id", nullable = false)
    private Long profesionalId;

    @Column(name = "ocurrido_en", nullable = false)
    private LocalDateTime ocurridoEn;

    @Column(name = "registrado_en", nullable = false)
    private LocalDateTime registradoEn;

    protected RegistroAuditoria() {
    }

    public RegistroAuditoria(String idEvento, String tipoEvento, Long turnoId, Long pacienteId,
                             Long profesionalId, LocalDateTime ocurridoEn) {
        this.idEvento = idEvento;
        this.tipoEvento = tipoEvento;
        this.turnoId = turnoId;
        this.pacienteId = pacienteId;
        this.profesionalId = profesionalId;
        this.ocurridoEn = ocurridoEn;
        this.registradoEn = LocalDateTime.now();
    }

    public Long getId() {
        return id;
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

    public LocalDateTime getOcurridoEn() {
        return ocurridoEn;
    }

    public LocalDateTime getRegistradoEn() {
        return registradoEn;
    }
}
