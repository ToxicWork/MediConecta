package ar.edu.mediconecta.negocio.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Una solicitud de la cola de autorizaciones, para el monitor (sin datos del paciente). */
public class FilaAutorizacionDTO implements Serializable {

    private final String idSolicitud;
    private final Long turnoId;
    private final LocalDateTime fechaHoraTurno;
    private final String obraSocial;
    private final String estado;
    private final String estadoEtiqueta;
    private final String codigoAutorizacion;
    private final String motivo;
    private final Integer intento;
    private final long fallas;
    private final String procesadaPor;
    private final LocalDateTime creadaEn;
    private final Long latenciaMs;

    public FilaAutorizacionDTO(String idSolicitud, Long turnoId, LocalDateTime fechaHoraTurno, String obraSocial,
                               String estado, String estadoEtiqueta, String codigoAutorizacion, String motivo,
                               Integer intento, long fallas, String procesadaPor, LocalDateTime creadaEn, Long latenciaMs) {
        this.idSolicitud = idSolicitud;
        this.turnoId = turnoId;
        this.fechaHoraTurno = fechaHoraTurno;
        this.obraSocial = obraSocial;
        this.estado = estado;
        this.estadoEtiqueta = estadoEtiqueta;
        this.codigoAutorizacion = codigoAutorizacion;
        this.motivo = motivo;
        this.intento = intento;
        this.fallas = fallas;
        this.procesadaPor = procesadaPor;
        this.creadaEn = creadaEn;
        this.latenciaMs = latenciaMs;
    }

    public String getIdSolicitud() {
        return idSolicitud;
    }

    public String getIdCorto() {
        return idSolicitud.substring(0, 8);
    }

    public Long getTurnoId() {
        return turnoId;
    }

    public LocalDateTime getFechaHoraTurno() {
        return fechaHoraTurno;
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public String getEstado() {
        return estado;
    }

    public String getEstadoEtiqueta() {
        return estadoEtiqueta;
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

    public long getFallas() {
        return fallas;
    }

    public String getProcesadaPor() {
        return procesadaPor;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public Long getLatenciaMs() {
        return latenciaMs;
    }
}
