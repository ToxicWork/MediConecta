package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "notificacion")
public class Notificacion implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "destinatario_id", nullable = false)
    private Long destinatarioId;

    @Column(name = "destino", nullable = false)
    private String destino;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 20)
    private CanalNotificacion canal;

    @Column(name = "asunto", nullable = false)
    private String asunto;

    @Column(name = "cuerpo", nullable = false, length = 1000)
    private String cuerpo;

    @Column(name = "id_evento", nullable = false, length = 36)
    private String idEvento;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "leida", nullable = false)
    private boolean leida;

    protected Notificacion() {
    }

    public Notificacion(Long destinatarioId, String destino, CanalNotificacion canal,
                        String asunto, String cuerpo, String idEvento) {
        this.destinatarioId = destinatarioId;
        this.destino = destino;
        this.canal = canal;
        this.asunto = asunto;
        this.cuerpo = cuerpo;
        this.idEvento = idEvento;
        this.creadaEn = LocalDateTime.now();
    }

    public void marcarLeida() {
        this.leida = true;
    }

    public Long getId() {
        return id;
    }

    public Long getDestinatarioId() {
        return destinatarioId;
    }

    public String getDestino() {
        return destino;
    }

    public CanalNotificacion getCanal() {
        return canal;
    }

    public String getAsunto() {
        return asunto;
    }

    public String getCuerpo() {
        return cuerpo;
    }

    public String getIdEvento() {
        return idEvento;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public boolean isLeida() {
        return leida;
    }
}
