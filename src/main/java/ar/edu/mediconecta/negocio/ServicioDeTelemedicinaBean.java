package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.integracion.videoconsulta.SalaVideoconsulta;
import ar.edu.mediconecta.integracion.videoconsulta.VideoconsultaGateway;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.AccesoDenegadoException;
import ar.edu.mediconecta.negocio.excepciones.VideoconsultaNoDisponibleException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;

/**
 * La sala se crea por REST en el momento de iniciar la consulta (integración sincrónica: el profesional
 * necesita el enlace para entrar). Sólo el profesional del turno la abre; el paciente se une con su enlace.
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL"})
public class ServicioDeTelemedicinaBean implements ServicioDeTelemedicina {

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private VideoconsultaGateway video;

    @Resource
    private SessionContext contexto;

    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public List<Turno> turnosVirtualesDelUsuarioActual() {
        return usuarioActual() == null ? List.of() : turnoDAO.buscarVirtualesConfirmadosDeUsuario(usuarioActual().getId());
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    public Turno iniciarVideoconsulta(Long turnoId) {
        Usuario profesional = usuarioActual();
        Turno turno = turnoDAO.buscarPorId(turnoId);
        if (turno == null || profesional == null || !turno.esDelProfesional(profesional.getId())) {
            throw new AccesoDenegadoException("El turno no existe o no es de tu agenda.");
        }
        if (turno.getEstado() != EstadoTurno.CONFIRMADO || !turno.isTelemedicina()) {
            throw new AccesoDenegadoException("Sólo se puede iniciar la videoconsulta de un turno de telemedicina confirmado.");
        }
        if (turno.getSalaId() != null) {
            return turno;
        }
        try {
            SalaVideoconsulta sala = video.crearSala("turno-" + turno.getId(),
                    "Teleconsulta de " + turno.getProfesional().getEspecialidad(),
                    turno.getProfesional().getNombreCompleto(), turno.getPaciente().getNombreCompleto());
            turno.asignarSala(sala.id(), sala.urlProfesional(), sala.urlPaciente());
            return turno;
        } catch (IntegracionNoDisponibleException falla) {
            throw new VideoconsultaNoDisponibleException();
        }
    }

    private Usuario usuarioActual() {
        return usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName()).orElse(null);
    }
}
