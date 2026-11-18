package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.integracion.videoconsulta.SalaVideoconsulta;
import ar.edu.mediconecta.integracion.videoconsulta.VideoconsultaGateway;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.PublicadorEventosTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Inject
    private PublicadorEventosTurno publicadorEventos;

    @Resource
    private SessionContext contexto;

    /** Las teleconsultas de hoy y las próximas, más las anteriores que quedaron con la sala abierta (para cerrarlas). */
    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public List<Turno> turnosVirtualesDelUsuarioActual() {
        Usuario usuario = usuarioActual();
        return usuario == null ? List.of()
                : turnoDAO.buscarVirtualesConfirmadosDeUsuario(usuario.getId(), LocalDate.now().atStartOfDay());
    }

    /**
     * D-14: el profesional termina la videoconsulta. Se cierra la sala en el proveedor (si no responde, la sala sigue
     * abierta y se informa) y el turno sale de la lista de Telemedicina de los dos.
     */
    @Override
    @RolesAllowed("PROFESIONAL")
    public Turno finalizarVideoconsulta(Long turnoId) {
        Usuario profesional = usuarioActual();
        Turno turno = turnoId == null ? null : turnoDAO.buscarPorIdParaActualizar(turnoId); // D-36
        if (turno == null || profesional == null || !turno.esDelProfesional(profesional.getId())) {
            throw new AccesoDenegadoException("El turno no existe o no es de tu agenda.");
        }
        if (!turno.isSalaAbierta()) {
            throw new AccesoDenegadoException("La videoconsulta de este turno no está en curso.");
        }
        try {
            video.cerrarSala(turno.getSalaId());
        } catch (IntegracionNoDisponibleException falla) {
            throw new VideoconsultaNoDisponibleException();
        }
        turno.cerrarSala(LocalDateTime.now());
        return turno;
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    public Turno iniciarVideoconsulta(Long turnoId) {
        Usuario profesional = usuarioActual();
        Turno turno = turnoId == null ? null : turnoDAO.buscarPorIdParaActualizar(turnoId); // D-36
        if (turno == null || profesional == null || !turno.esDelProfesional(profesional.getId())) {
            throw new AccesoDenegadoException("El turno no existe o no es de tu agenda.");
        }
        if (turno.getEstado() != EstadoTurno.CONFIRMADO || !turno.isTelemedicina()) {
            throw new AccesoDenegadoException("Sólo se puede iniciar la videoconsulta de un turno de telemedicina confirmado.");
        }
        if (turno.getSalaCerradaEn() != null) {
            throw new AccesoDenegadoException("La videoconsulta de este turno ya terminó.");
        }
        if (turno.getSalaId() != null) {
            return turno;
        }
        try {
            SalaVideoconsulta sala = video.crearSala("turno-" + turno.getId(),
                    "Teleconsulta de " + turno.getProfesional().getEspecialidad(),
                    turno.getProfesional().getNombreCompleto(), turno.getPaciente().getNombreCompleto());
            turno.asignarSala(sala.id(), sala.urlProfesional(), sala.urlPaciente());
            // D-31: el aviso al paciente viaja por el tópico, como los demás; sale recién con el commit.
            publicadorEventos.publicar(EventoTurno.builder().tipo(TipoEventoTurno.SALA_ABIERTA).turno(turno).build());
            return turno;
        } catch (IntegracionNoDisponibleException falla) {
            throw new VideoconsultaNoDisponibleException();
        }
    }

    private Usuario usuarioActual() {
        return usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName()).orElse(null);
    }
}
