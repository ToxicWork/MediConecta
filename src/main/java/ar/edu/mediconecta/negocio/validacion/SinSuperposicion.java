package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;

import java.time.LocalDateTime;

public class SinSuperposicion extends ValidadorReserva {

    /** Consulta que necesita el eslabón; en producción la resuelve {@code TurnoDAO}, en las pruebas un lambda. */
    @FunctionalInterface
    public interface ConsultaSuperposicion {
        boolean pacienteTieneConfirmadoEn(Long pacienteId, LocalDateTime fechaHora, Long excluirTurnoId);
    }

    private final ConsultaSuperposicion consulta;

    public SinSuperposicion(ConsultaSuperposicion consulta) {
        this.consulta = consulta;
    }

    @Override
    protected void verificar(SolicitudReserva solicitud) {
        if (consulta.pacienteTieneConfirmadoEn(solicitud.pacienteId(), solicitud.turno().getFechaHora(),
                solicitud.turno().getId())) {
            throw new ReservaInvalidaException("Ya tenés otro turno confirmado en ese mismo horario.");
        }
    }
}
