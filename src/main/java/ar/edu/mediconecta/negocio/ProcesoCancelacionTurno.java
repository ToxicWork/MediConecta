package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.PublicadorEventosTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.excepciones.TurnoNoDisponibleException;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDateTime;

/**
 * Cancelación de un turno por su paciente, en una transacción (REQUIRED). Es sin estado para que la usen las
 * dos capas de presentación: la web (a través de {@code ServicioDeTurnos}) y la API REST.
 *
 * <p>Si el turno se puede cancelar o no lo decide el <b>patrón State</b>, no este servicio: acá sólo se
 * verifica la titularidad y se llama a {@link Turno#cancelar()}. Si el estado actual no admite la operación
 * (ya cancelado, todavía en espera), el estado lanza {@code TransicionInvalidaException}, la transacción se
 * revierte y la API responde 409.
 *
 * <p>El turno cancelado queda como historial (con su pago y autorización) y el horario se vuelve a ofrecer
 * como un turno nuevo. El reembolso, si hubo cobro, lo hace Facturación al consumir TURNO_CANCELADO: recién
 * después del commit, así nunca se devuelve dinero de una cancelación que no se concretó.
 */
@Stateless
@DeclareRoles({"PACIENTE"})
@RolesAllowed("PACIENTE")
public class ProcesoCancelacionTurno {

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private PublicadorEventosTurno publicadorEventos;

    public Turno cancelar(Long turnoId, Long pacienteId) {
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null || !turno.perteneceAPaciente(pacienteId)) {
            throw new TurnoNoDisponibleException("El turno no existe o no te pertenece.");
        }
        turno.cancelar(); // State: sólo CONFIRMADO -> CANCELADO; otro estado lanza TransicionInvalidaException
        turnoDAO.actualizar(turno);
        if (turno.getFechaHora().isAfter(LocalDateTime.now())) {
            turnoDAO.guardar(turno.horarioLiberado());
        }
        publicadorEventos.publicar(EventoTurno.builder()
                .tipo(TipoEventoTurno.TURNO_CANCELADO)
                .turno(turno)
                .build());
        return turno;
    }
}
