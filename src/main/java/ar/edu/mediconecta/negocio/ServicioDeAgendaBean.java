package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL"})
public class ServicioDeAgendaBean implements ServicioDeAgenda {

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private TurnoDAO turnoDAO;

    @Resource
    private SessionContext contexto;

    @Override
    @PermitAll
    public List<Profesional> profesionales() {
        return usuarioDAO.listarProfesionales();
    }

    @Override
    @PermitAll
    public List<Turno> disponibilidadDe(Long profesionalId) {
        return turnoDAO.buscarDisponibles(profesionalId);
    }

    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public List<Turno> turnosDelUsuarioActual() {
        Usuario usuario = usuarioActual();
        return usuario == null ? List.of() : turnoDAO.buscarDeUsuario(usuario.getId());
    }

    /** Titularidad: un turno ajeno se informa como inexistente, para no revelar que el id existe (IDOR). */
    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public Optional<Turno> turnoDelUsuarioActual(Long turnoId) {
        Usuario usuario = usuarioActual();
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (usuario == null || turno == null) {
            return Optional.empty();
        }
        boolean propio = turno.perteneceAPaciente(usuario.getId()) || turno.esDelProfesional(usuario.getId());
        return propio ? Optional.of(turno) : Optional.empty();
    }

    private Usuario usuarioActual() {
        return usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName()).orElse(null);
    }
}
