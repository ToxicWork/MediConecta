package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.ProfesionalApi;
import ar.edu.mediconecta.api.dto.TurnoDisponibleApi;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.ServicioDeAgenda;
import ar.edu.mediconecta.negocio.catalogo.CatalogoPrestaciones;
import jakarta.annotation.security.PermitAll;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
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
        List<Turno> libres = agenda.disponibilidadDe(profesionalId);
        // D-48: un id que no es de un profesional es 404, no una lista vacía. Sólo se consulta si no hay horarios,
        // así el camino normal (el que mide la prueba de carga) no suma una consulta.
        if (libres.isEmpty() && !agenda.existeProfesional(profesionalId)) {
            throw new NotFoundException("No existe ese profesional.");
        }
        return libres.stream()
                .map(t -> EnsambladorApi.disponible(t, catalogo.para(t)))
                .toList();
    }
}
