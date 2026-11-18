package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.TurnoApi;
import ar.edu.mediconecta.dao.PedidoIdempotenteDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.PedidoIdempotente;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ProcesoCancelacionTurno;
import ar.edu.mediconecta.negocio.ServicioDeAgenda;
import ar.edu.mediconecta.negocio.excepciones.AccesoDenegadoException;
import ar.edu.mediconecta.negocio.excepciones.ClaveDeIdempotenciaReutilizadaException;
import ar.edu.mediconecta.negocio.excepciones.TurnoInexistenteException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code GET /api/v1/turnos}, {@code GET /api/v1/turnos/{id}} y {@code POST /api/v1/turnos/{id}/cancelacion}:
 * turnos del usuario autenticado (JWT en {@code Authorization: Bearer}).
 * Seguridad declarativa con {@code @RolesAllowed} en el recurso (es un EJB; {@link FiltroDeRoles} la aplica antes de
 * entrar) y titularidad en el servicio: pedir el turno de otro devuelve 404, igual que uno inexistente.
 *
 * <p>Concurrencia (Eje C): {@code GET /turnos/{id}} devuelve la versión del turno como {@code ETag} (y 304 con
 * {@code If-None-Match} si no cambió); la cancelación acepta {@code If-Match} y responde <b>412</b> si el turno
 * cambió desde que el cliente lo leyó. Con {@code Idempotency-Key}, repetir la cancelación devuelve la misma
 * respuesta que la primera vez (con {@code Idempotent-Replayed: true}) sin volver a hacerla.
 */
@Path("/v1/turnos")
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL", "ADMINISTRATIVO"})
@RolesAllowed({"PACIENTE", "PROFESIONAL"})
@Produces(MediaType.APPLICATION_JSON)
public class TurnosResource {

    private static final Jsonb JSON = JsonbBuilder.create();

    @Inject
    private ServicioDeAgenda agenda;

    @Inject
    private ProcesoCancelacionTurno cancelacion;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private PedidoIdempotenteDAO pedidos;

    @Resource
    private SessionContext contexto;

    @GET
    public List<TurnoApi> misTurnos() {
        Usuario usuario = usuarioActual();
        return agenda.turnosDelUsuarioActual().stream().map(t -> EnsambladorApi.turno(t, usuario)).toList();
    }

    @GET
    @Path("/{id}")
    public Response turno(@PathParam("id") Long turnoId, @HeaderParam(HttpHeaders.IF_NONE_MATCH) String siNoCoincide) {
        Usuario usuario = usuarioActual();
        // Excepción de aplicación (no la NotFoundException de JAX-RS): el contenedor EJB no la registra como un
        // error de sistema con traza (D-34) y el mapeador responde 404, igual que para un id inexistente.
        Turno turno = agenda.turnoDelUsuarioActual(turnoId).orElseThrow(TurnoInexistenteException::new);
        String etiqueta = Versiones.etiqueta(turno.getVersion());
        if (Versiones.coincideIfNoneMatch(siNoCoincide, turno.getVersion())) {
            return Response.notModified().header(HttpHeaders.ETAG, etiqueta).build();
        }
        return Response.ok(EnsambladorApi.turno(turno, usuario)).header(HttpHeaders.ETAG, etiqueta).build();
    }

    /**
     * {@code POST /api/v1/turnos/{id}/cancelacion}: cancela un turno. Puede el paciente (sus turnos), el profesional
     * (los de su agenda) o un administrativo (cualquiera); la titularidad la verifica {@link ProcesoCancelacionTurno}.
     * La cancelación se modela como un sub-recurso que se crea, porque es un cambio de estado con efectos (reintegro,
     * aviso) y no un borrado: el turno sigue existiendo como historial.
     *
     * <p>200 con el turno cancelado y su ETag nueva; 404 si no existe o es ajeno; <b>412</b> si trae {@code If-Match}
     * y el turno cambió; <b>409</b> si el estado actual no admite la cancelación (patrón State): cancelar dos veces el
     * mismo turno, o uno que todavía está en espera o que ya empezó. Con {@code Idempotency-Key}: un reintento con la
     * misma clave recibe la respuesta guardada; la misma clave en otro pedido, 422.
     */
    @POST
    @Path("/{id}/cancelacion")
    @RolesAllowed({"PACIENTE", "PROFESIONAL", "ADMINISTRATIVO"})
    public Response cancelar(@PathParam("id") Long turnoId, @HeaderParam(HttpHeaders.IF_MATCH) String siCoincide,
                             @HeaderParam(Idempotencia.CABECERA) String claveRecibida) {
        String clave = Idempotencia.validar(claveRecibida);
        Usuario usuario = usuarioActual();
        String operacion = "POST /api/v1/turnos/" + turnoId + "/cancelacion";
        if (clave != null) {
            // Con el turno bloqueado, un reintento simultáneo espera a que termine el primero y encuentra su respuesta.
            turnoDAO.buscarPorIdParaActualizar(turnoId);
            Optional<PedidoIdempotente> anterior = pedidos.buscar(usuario.getId(), clave);
            if (anterior.isPresent()) {
                return repetir(anterior.get(), operacion);
            }
        }
        Turno cancelado = cancelacion.cancelar(turnoId, Versiones.aceptadasPorIfMatch(siCoincide).orElse(null));
        turnoDAO.sincronizar(); // la versión ya es la nueva: es la ETag de la respuesta
        TurnoApi cuerpo = EnsambladorApi.turno(cancelado, usuario);
        String etiqueta = Versiones.etiqueta(cancelado.getVersion());
        if (clave != null) {
            pedidos.guardar(new PedidoIdempotente(usuario.getId(), clave, operacion, 200, JSON.toJson(cuerpo), etiqueta,
                    LocalDateTime.now()));
        }
        return Response.ok(cuerpo).header(HttpHeaders.ETAG, etiqueta).build();
    }

    private static Response repetir(PedidoIdempotente anterior, String operacion) {
        if (!anterior.esDeLaOperacion(operacion)) {
            throw new ClaveDeIdempotenciaReutilizadaException();
        }
        return Response.status(anterior.getEstadoHttp())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(anterior.getRespuesta())
                .header(HttpHeaders.ETAG, anterior.getEtag())
                .header(Idempotencia.REPETIDA, "true")
                .build();
    }

    private Usuario usuarioActual() {
        return usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName())
                .orElseThrow(() -> new AccesoDenegadoException("Tu usuario ya no existe."));
    }
}
