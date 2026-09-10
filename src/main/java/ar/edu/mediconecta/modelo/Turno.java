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
import java.time.LocalDateTime;

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

    @Version
    private int version;

    protected Turno() {
    }

    public Turno(Profesional profesional, LocalDateTime fechaHora) {
        this.profesional = profesional;
        this.fechaHora = fechaHora;
        this.estado = EstadoTurno.DISPONIBLE;
    }

    public void marcarEnHold(Paciente paciente) {
        this.paciente = paciente;
        this.estado = EstadoTurno.HOLD;
    }

    public void confirmar() {
        this.estado = EstadoTurno.CONFIRMADO;
    }

    public void liberar() {
        this.paciente = null;
        this.estado = EstadoTurno.DISPONIBLE;
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
}
