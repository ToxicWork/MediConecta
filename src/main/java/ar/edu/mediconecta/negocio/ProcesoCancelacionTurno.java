package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.PublicadorEventosTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.TurnoInexistenteException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDateTime;

/**
 * Cancelación de un turno, en una transacción (REQUIRED). Es sin estado para que la usen las dos capas de
 * presentación: la web (agenda del paciente y del profesional, pantalla Turnos del administrativo) y la API REST.
 *
 * <p>Quién puede cancelar (matriz de permisos del diseño, punto 8.2): seguridad declarativa para el rol
 * ({@code @RolesAllowed}) y programática para la titularidad:
 * <ul>
 *   <li>el paciente, sus propios turnos;</li>
 *   <li>el profesional, los turnos de su agenda;</li>
 *   <li>el administrativo, cualquier turno.</li>
 * </ul>
 * Un turno ajeno se informa igual que uno inexistente ({@link TurnoInexistenteException}), para no revelar ids.
 *
 * <p>Si el turno se puede cancelar o no lo decide el <b>patrón State</b>, no este servicio: si el estado actual no
 * admite la operación (ya cancelado, todavía en espera, o confirmado pero ya empezado), el estado lanza
 * {@code TransicionInvalidaException}, la transacción se revierte y la API responde 409.
 *
 * <p>El turno cancelado queda como historial (con su pago, su autorización y quién lo canceló). El horario se
 * vuelve a ofrecer sólo si canceló el paciente ({@link Turno#liberaElHorario}). El reembolso, si hubo cobro, lo
 * hace Facturación al consumir TURNO_CANCELADO: recién después del commit, así nunca se devuelve dinero de una
 * cancelación que no se concretó. Los avisos dicen quién canceló.
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL", "ADMINISTRATIVO"})
@RolesAllowed({"PACIENTE", "PROFESIONAL", "ADMINISTRATIVO"})
public class ProcesoCancelacionTurno {

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private PublicadorEventosTurno publicadorEventos;

    @Resource
    private SessionContext contexto;

    public Turno cancelar(Long turnoId) {
        Usuario quien = usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName())
                .orElseThrow(TurnoInexistenteException::new);
        // D-36: si otro lo está modificando (la autorización de la obra social, otra cancelación), se espera y se lee
        // la versión nueva: una cancelación repetida recibe 409, nunca un error 500.
        Turno turno = turnoId == null ? null : turnoDAO.buscarPorIdParaActualizar(turnoId);
        if (turno == null || !puedeCancelar(quien, turno)) {
            throw new TurnoInexistenteException();
        }
        LocalDateTime ahora = LocalDateTime.now();
        // State: sólo CONFIRMADO y antes de la hora del turno -> CANCELADO; si no, TransicionInvalidaException (409)
        turno.cancelar(quien.getRol(), ahora);
        turnoDAO.actualizar(turno);
        if (turno.liberaElHorario(ahora)) {
            turnoDAO.guardar(turno.horarioLiberado());
        }
        publicadorEventos.publicar(EventoTurno.builder()
                .tipo(TipoEventoTurno.TURNO_CANCELADO)
                .turno(turno)
                .build());
        return turno;
    }

    /** Titularidad (seguridad programática): el rol ya lo verificó {@code @RolesAllowed}. */
    static boolean puedeCancelar(Usuario quien, Turno turno) {
        return switch (quien.getRol()) {
            case PACIENTE -> turno.perteneceAPaciente(quien.getId());
            case PROFESIONAL -> turno.esDelProfesional(quien.getId());
            case ADMINISTRATIVO -> true;
        };
    }
}
