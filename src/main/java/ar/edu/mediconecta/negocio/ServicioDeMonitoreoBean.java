package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.EventoProcesadoDAO;
import ar.edu.mediconecta.dao.EventoPublicadoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.mensajeria.Suscripciones;
import ar.edu.mediconecta.modelo.EventoProcesado;
import ar.edu.mediconecta.modelo.EventoPublicado;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.dto.EstadoSuscriptorDTO;
import ar.edu.mediconecta.negocio.dto.FilaMonitorDTO;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Monitor de la mensajería: cruza los eventos publicados con lo que registró cada suscriptor
 * para mostrar, por evento, quién lo procesó y cuánto tardó, o quién lo tiene pendiente.
 *
 * <p>Seguridad: {@code @RolesAllowed} decide quién entra (declarativa), y el usuario se toma del
 * principal autenticado y no de un parámetro, así nadie puede pedir los eventos de otro cambiando
 * un id (programática, por titularidad).
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL"})
@RolesAllowed({"PACIENTE", "PROFESIONAL"})
public class ServicioDeMonitoreoBean implements ServicioDeMonitoreo {

    private static final int MAXIMO_EVENTOS = 20;

    @Resource
    private SessionContext contexto;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private EventoPublicadoDAO eventoPublicadoDAO;

    @Inject
    private EventoProcesadoDAO eventoProcesadoDAO;

    @Override
    public List<FilaMonitorDTO> eventosRecientesDelUsuarioActual() {
        Usuario usuario = usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName()).orElse(null);
        if (usuario == null) {
            return List.of();
        }
        List<EventoPublicado> eventos = eventoPublicadoDAO.buscarRecientesDeUsuario(usuario.getId(), MAXIMO_EVENTOS);

        List<String> claves = eventos.stream()
                .flatMap(e -> Suscripciones.TODAS.stream().map(s -> EventoProcesado.clave(s, e.getIdEvento())))
                .toList();
        Map<String, LocalDateTime> procesadoEn = eventoProcesadoDAO.buscarPorClaves(claves).stream()
                .collect(Collectors.toMap(EventoProcesado::getClave, EventoProcesado::getProcesadoEn));

        return eventos.stream().map(e -> new FilaMonitorDTO(e.getIdEvento(), e.getTipoEvento(), e.getTurnoId(),
                e.getFechaHoraTurno(), e.getPublicadoEn(),
                Suscripciones.TODAS.stream().map(s -> estado(s, e, procesadoEn)).toList())).toList();
    }

    private static EstadoSuscriptorDTO estado(String suscriptor, EventoPublicado evento,
                                              Map<String, LocalDateTime> procesadoEn) {
        LocalDateTime cuando = procesadoEn.get(EventoProcesado.clave(suscriptor, evento.getIdEvento()));
        if (cuando == null) {
            return new EstadoSuscriptorDTO(suscriptor, false, null);
        }
        return new EstadoSuscriptorDTO(suscriptor, true, Duration.between(evento.getPublicadoEn(), cuando).toMillis());
    }
}
