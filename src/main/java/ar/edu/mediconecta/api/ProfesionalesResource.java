package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.ProfesionalApi;
import ar.edu.mediconecta.api.dto.TurnoDisponibleApi;
import ar.edu.mediconecta.negocio.ServicioDeAgenda;
import ar.edu.mediconecta.negocio.catalogo.CatalogoPrestaciones;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * {@code GET /api/v1/profesionales} y {@code GET /api/v1/profesionales/{id}/disponibilidad}: información
 * pública de la cartilla (sin autenticación), como la que consumiría un portal de salud.
 */
@Path("/v1/profesionales")
@Stateless
@PermitAll
@Produces(MediaType.APPLICATION_JSON)
public class ProfesionalesResource {

    @Inject
    private ServicioDeAgenda agenda;

    @Inject
    private CatalogoPrestaciones catalogo;

    @GET
    public List<ProfesionalApi> listar() {
        return agenda.profesionales().stream().map(EnsambladorApi::profesional).toList();
    }

    @GET
    @Path("/{id}/disponibilidad")
    public List<TurnoDisponibleApi> disponibilidad(@PathParam("id") Long profesionalId) {
        return agenda.disponibilidadDe(profesionalId).stream()
                .map(t -> EnsambladorApi.disponible(t, catalogo.para(t)))
                .toList();
    }
}
