package ar.edu.mediconecta.negocio.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Transfer Object de una fila del monitor de eventos: solo lo que la vista necesita, sin
 * exponer entidades JPA ni datos de los usuarios.
 */
public class FilaMonitorDTO implements Serializable {

    private final String idEvento;
    private final String tipoEvento;
    private final Long turnoId;
    private final LocalDateTime fechaHoraTurno;
    private final LocalDateTime publicadoEn;
    private final List<EstadoSuscriptorDTO> suscriptores;

    public FilaMonitorDTO(String idEvento, String tipoEvento, Long turnoId, LocalDateTime fechaHoraTurno,
                          LocalDateTime publicadoEn, List<EstadoSuscriptorDTO> suscriptores) {
        this.idEvento = idEvento;
        this.tipoEvento = tipoEvento;
        this.turnoId = turnoId;
        this.fechaHoraTurno = fechaHoraTurno;
        this.publicadoEn = publicadoEn;
        this.suscriptores = suscriptores;
    }

    public String getIdEvento() {
        return idEvento;
    }

    public String getIdCorto() {
        return idEvento.substring(0, 8);
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getEtiqueta() {
        return switch (tipoEvento) {
            case "TURNO_CONFIRMADO" -> "Confirmado";
            case "TURNO_CANCELADO" -> "Cancelado";
            case "TURNO_AUTORIZADO" -> "Autorizado";
            case "AUTORIZACION_RECHAZADA" -> "Autorización rechazada";
            default -> tipoEvento;
        };
    }

    /** Clase CSS del tag según el tipo de evento. */
    public String getEstilo() {
        return switch (tipoEvento) {
            case "TURNO_CONFIRMADO", "TURNO_AUTORIZADO" -> "tag-success";
            case "TURNO_CANCELADO", "AUTORIZACION_RECHAZADA" -> "tag-danger";
            default -> "tag-info";
        };
    }

    public boolean isConfirmacion() {
        return "TURNO_CONFIRMADO".equals(tipoEvento);
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

    public List<EstadoSuscriptorDTO> getSuscriptores() {
        return suscriptores;
    }

    public boolean isCompleto() {
        return suscriptores.stream().noneMatch(EstadoSuscriptorDTO::isPendiente);
    }
}
