package ar.edu.mediconecta.mensajeria;

import ar.edu.mediconecta.modelo.SolicitudAutorizacion;
import ar.edu.mediconecta.modelo.Turno;

import java.time.LocalDateTime;

/** Cuerpo de los mensajes de {@code cola.autorizaciones}: lo que el legado necesita para autorizar. */
public record MensajeAutorizacion(
        String idSolicitud,
        Long turnoId,
        String obraSocial,
        String plan,
        String nroAfiliado,
        String codigoPrestacion,
        LocalDateTime fechaPrestacion,
        String matriculaPrestador) {

    public static MensajeAutorizacion de(SolicitudAutorizacion solicitud, Turno turno, String plan) {
        return new MensajeAutorizacion(solicitud.getIdSolicitud(), turno.getId(), solicitud.getObraSocial(), plan,
                turno.getPaciente().getNroAfiliado(), solicitud.getCodigoPrestacion(), turno.getFechaHora(),
                turno.getProfesional().getMatricula());
    }
}
