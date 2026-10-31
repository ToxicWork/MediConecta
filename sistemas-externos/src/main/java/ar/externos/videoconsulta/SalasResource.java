package ar.externos.videoconsulta;

import ar.externos.consola.EstadoSimuladores;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Map;
import java.util.UUID;

/** API REST del proveedor de videoconsulta (partner moderno, simulado). Autenticación por {@code X-Api-Key}. */
@Path("/video/v1/salas")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SalasResource {

    static final String API_KEY = "vk_test_mediconecta_2026";

    @Inject
    private RegistroSalas registro;

    @Inject
    private EstadoSimuladores estado;

    @Context
    private UriInfo uri;

    @POST
    public Response crear(@HeaderParam("X-Api-Key") String apiKey, PedidoSala pedido) {
        Response rechazo = validar(apiKey, "POST /salas");
        if (rechazo != null) {
            return rechazo;
        }
        if (pedido == null || pedido.referencia() == null) {
            return Response.status(422).entity(Map.of("error", "pedido_invalido")).build();
        }
        String base = uri.getBaseUri().toString().replaceAll("/api/?$", "");
        Sala sala = new Sala("sala-" + UUID.randomUUID().toString().substring(0, 8), pedido.referencia(),
                pedido.titulo(), pedido.profesional(), pedido.paciente(), base);
        registro.guardar(sala);
        estado.registrar("Videoconsulta (REST)", "POST /salas " + pedido.referencia(), "201 " + sala.getId());
        return Response.status(Response.Status.CREATED).entity(sala).build();
    }

    @GET
    @Path("/{id}")
    public Response consultar(@HeaderParam("X-Api-Key") String apiKey, @PathParam("id") String id) {
        // La página de la sala la consulta sin API key para mostrar los participantes.
        return registro.buscar(id).map(s -> Response.ok(s).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "sala_inexistente")).build());
    }

    @DELETE
    @Path("/{id}")
    public Response cerrar(@HeaderParam("X-Api-Key") String apiKey, @PathParam("id") String id) {
        Response rechazo = validar(apiKey, "DELETE /salas/" + id);
        if (rechazo != null) {
            return rechazo;
        }
        return registro.buscar(id).map(s -> {
            s.cerrar();
            estado.registrar("Videoconsulta (REST)", "DELETE /salas/" + id, "200 CERRADA");
            return Response.ok(s).build();
        }).orElse(Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "sala_inexistente")).build());
    }

    private Response validar(String apiKey, String operacion) {
        if (estado.getVideoconsulta() == EstadoSimuladores.ModoServicio.CAIDO) {
            estado.registrar("Videoconsulta (REST)", operacion, "503 servicio no disponible");
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .entity(Map.of("error", "servicio_no_disponible")).build();
        }
        if (!API_KEY.equals(apiKey)) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "api_key_invalida")).build();
        }
        return null;
    }

    public record PedidoSala(String referencia, String titulo, String profesional, String paciente) {
    }
}
