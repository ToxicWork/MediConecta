package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.TurnoApi;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ProcesoCancelacionTurno;
import ar.edu.mediconecta.negocio.ServicioDeAgenda;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * {@code GET /api/v1/turnos}, {@code GET /api/v1/turnos/{id}} y {@code POST /api/v1/turnos/{id}/cancelacion}:
 * turnos del usuario autenticado (JWT en {@code Authorization: Bearer}).
 * Seguridad declarativa con {@code @RolesAllowed} en el recurso (es un EJB) y titularidad en el servicio:
 * pedir el turno de otro devuelve 404, igual que uno inexistente.
 */
@Path("/v1/turnos")
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL", "ADMINISTRATIVO"})
@RolesAllowed({"PACIENTE", "PROFESIONAL"})
@Produces(MediaType.APPLICATION_JSON)
public class TurnosResource {

    @Inject
    private ServicioDeAgenda agenda;

    @Inject
    private ProcesoCancelacionTurno cancelacion;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Resource
    private SessionContext contexto;

    @GET
    public List<TurnoApi> misTurnos() {
        Usuario usuario = usuarioActual();
        return agenda.turnosDelUsuarioActual().stream().map(t -> EnsambladorApi.turno(t, usuario)).toList();
    }

    @GET
    @Path("/{id}")
    public TurnoApi turno(@PathParam("id") Long turnoId) {
        Usuario usuario = usuarioActual();
        return agenda.turnoDelUsuarioActual(turnoId)
                .map(t -> EnsambladorApi.turno(t, usuario))
                .orElseThrow(() -> new NotFoundException("El turno no existe."));
    }

    /**
     * {@code POST /api/v1/turnos/{id}/cancelacion}: cancela un turno. Puede el paciente (sus turnos), el profesional
     * (los de su agenda) o un administrativo (cualquiera); la titularidad la verifica {@link ProcesoCancelacionTurno}.
     * La cancelación se modela como un sub-recurso que se crea, porque es un cambio de estado con efectos (reintegro,
     * aviso) y no un borrado: el turno sigue existiendo como historial.
     *
     * <p>200 con el turno cancelado; 404 si no existe o es ajeno; <b>409</b> si el estado actual no admite
     * la cancelación (patrón State): cancelar dos veces el mismo turno, o uno que todavía está en espera.
     */
    @POST
    @Path("/{id}/cancelacion")
    @RolesAllowed({"PACIENTE", "PROFESIONAL", "ADMINISTRATIVO"})
    public TurnoApi cancelar(@PathParam("id") Long turnoId) {
        return EnsambladorApi.turno(cancelacion.cancelar(turnoId), usuarioActual());
    }

    private Usuario usuarioActual() {
        return usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName()).orElseThrow(NotFoundException::new);
    }
}
