package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.EventoProcesadoDAO;
import ar.edu.mediconecta.dao.EventoPublicadoDAO;
import ar.edu.mediconecta.dao.LlamadaIntegracionDAO;
import ar.edu.mediconecta.dao.SolicitudAutorizacionDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.integracion.obrasocial.CoberturaGateway;
import ar.edu.mediconecta.integracion.pagos.PagosGateway;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.integracion.videoconsulta.VideoconsultaGateway;
import ar.edu.mediconecta.mensajeria.Suscripciones;
import ar.edu.mediconecta.modelo.EstadoSolicitud;
import ar.edu.mediconecta.modelo.EventoProcesado;
import ar.edu.mediconecta.modelo.EventoPublicado;
import ar.edu.mediconecta.modelo.LlamadaIntegracion;
import ar.edu.mediconecta.modelo.SolicitudAutorizacion;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.dto.EstadoSuscriptorDTO;
import ar.edu.mediconecta.negocio.dto.FilaAutorizacionDTO;
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
 * Monitor de la integración: cruza los eventos publicados con lo que registró cada suscriptor, muestra la
 * cola de autorizaciones y el estado de los sistemas externos.
 *
 * <p>Seguridad: {@code @RolesAllowed} decide quién entra (declarativa), y el usuario se toma del principal
 * autenticado y no de un parámetro, así nadie puede pedir los eventos de otro cambiando un id (programática).
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL"})
@RolesAllowed({"PACIENTE", "PROFESIONAL"})
public class ServicioDeMonitoreoBean implements ServicioDeMonitoreo {

    private static final int MAXIMO_EVENTOS = 20;
    private static final int MAXIMO_LLAMADAS = 25;

    @Resource
    private SessionContext contexto;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private EventoPublicadoDAO eventoPublicadoDAO;

    @Inject
    private EventoProcesadoDAO eventoProcesadoDAO;

    @Inject
    private SolicitudAutorizacionDAO solicitudDAO;

    @Inject
    private LlamadaIntegracionDAO llamadaDAO;

    @Inject
    private CoberturaGateway obraSocial;

    @Inject
    private PagosGateway pasarela;

    @Inject
    private VideoconsultaGateway videoconsulta;

    @Override
    public List<FilaMonitorDTO> eventosRecientesDelUsuarioActual() {
        Usuario usuario = usuarioActual();
        if (usuario == null) {
            return List.of();
        }
        List<EventoPublicado> eventos = eventoPublicadoDAO.buscarRecientesDeUsuario(usuario.getId(), MAXIMO_EVENTOS);

        List<String> claves = eventos.stream()
                .flatMap(e -> Suscripciones.DEL_TOPICO.stream().map(s -> EventoProcesado.clave(s, e.getIdEvento())))
                .toList();
        Map<String, LocalDateTime> procesadoEn = eventoProcesadoDAO.buscarPorClaves(claves).stream()
                .collect(Collectors.toMap(EventoProcesado::getClave, EventoProcesado::getProcesadoEn));

        return eventos.stream().map(e -> new FilaMonitorDTO(e.getIdEvento(), e.getTipoEvento(), e.getTurnoId(),
                e.getFechaHoraTurno(), e.getPublicadoEn(),
                Suscripciones.DEL_TOPICO.stream().map(s -> estado(s, e, procesadoEn)).toList())).toList();
    }

    @Override
    public List<FilaAutorizacionDTO> autorizacionesRecientesDelUsuarioActual() {
        Usuario usuario = usuarioActual();
        if (usuario == null) {
            return List.of();
        }
        List<SolicitudAutorizacion> solicitudes = solicitudDAO.buscarRecientesDeUsuario(usuario.getId(), MAXIMO_EVENTOS);
        Map<String, Long> fallas = llamadaDAO.contarFallasPorReferencia(
                solicitudes.stream().map(SolicitudAutorizacion::getIdSolicitud).toList());
        return solicitudes.stream().map(s -> new FilaAutorizacionDTO(s.getIdSolicitud(), s.getTurno().getId(),
                s.getTurno().getFechaHora(), s.getObraSocial(), s.getEstado().name(), s.getEstado().getEtiqueta(),
                s.getCodigoAutorizacion(), s.getMotivo(), s.getIntento(), fallas.getOrDefault(s.getIdSolicitud(), 0L),
                s.getProcesadaPor(), s.getCreadaEn(),
                s.getEstado() == EstadoSolicitud.PENDIENTE || s.getResueltaEn() == null ? null
                        : Duration.between(s.getCreadaEn(), s.getResueltaEn()).toMillis())).toList();
    }

    @Override
    public List<EstadoCircuito> circuitos() {
        return List.of(obraSocial.estadoCircuito(), pasarela.estadoCircuito(), videoconsulta.estadoCircuito());
    }

    @Override
    public List<LlamadaIntegracion> llamadasRecientes() {
        return llamadaDAO.buscarRecientes(MAXIMO_LLAMADAS);
    }

    private Usuario usuarioActual() {
        return usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName()).orElse(null);
    }

    private static EstadoSuscriptorDTO estado(String suscriptor, EventoPublicado evento,
                                              Map<String, LocalDateTime> procesadoEn) {
        if (!evento.esperaA(suscriptor)) {
            return new EstadoSuscriptorDTO(suscriptor, false, false, null);
        }
        LocalDateTime cuando = procesadoEn.get(EventoProcesado.clave(suscriptor, evento.getIdEvento()));
        if (cuando == null) {
            return new EstadoSuscriptorDTO(suscriptor, true, false, null);
        }
        return new EstadoSuscriptorDTO(suscriptor, true, true,
                Duration.between(evento.getPublicadoEn(), cuando).toMillis());
    }
}
