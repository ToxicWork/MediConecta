package ar.externos.consola;

import ar.externos.pasarela.RegistroPagos;
import ar.externos.videoconsulta.RegistroSalas;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

/**
 * Consola de la demo: permite ver y cambiar el estado de cada sistema externo simulado. No es parte
 * de MediConecta; es el "control remoto" para mostrar en vivo qué pasa cuando un tercero falla.
 */
@Path("/consola")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
public class ConsolaResource {

    @Inject
    private EstadoSimuladores estado;

    @Inject
    private RegistroPagos pagos;

    @Inject
    private RegistroSalas salas;

    @GET
    @Path("/estado")
    public Map<String, Object> estado() {
        return Map.of(
                "obraSocial", estado.getObraSocial(),
                "autorizaciones", estado.getAutorizaciones(),
                "pasarela", estado.getPasarela(),
                "videoconsulta", estado.getVideoconsulta(),
                "demoraAutorizacionMs", estado.getDemoraAutorizacionMs());
    }

    @PUT
    @Path("/estado")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, Object> cambiar(CambioEstado cambio) {
        if (cambio.obraSocial() != null) {
            estado.setObraSocial(cambio.obraSocial());
        }
        if (cambio.autorizaciones() != null) {
            estado.setAutorizaciones(cambio.autorizaciones());
        }
        if (cambio.pasarela() != null) {
            estado.setPasarela(cambio.pasarela());
        }
        if (cambio.videoconsulta() != null) {
            estado.setVideoconsulta(cambio.videoconsulta());
        }
        if (cambio.demoraAutorizacionMs() != null) {
            estado.setDemoraAutorizacionMs(cambio.demoraAutorizacionMs());
        }
        estado.registrar("Consola", "cambio de estado", estado().toString());
        return estado();
    }

    @GET
    @Path("/registro")
    public List<EstadoSimuladores.RegistroLlamada> registro() {
        return estado.getRegistro();
    }

    @GET
    @Path("/pagos")
    public List<?> pagos() {
        return pagos.todos();
    }

    @GET
    @Path("/salas")
    public List<?> salas() {
        return salas.todas();
    }

    public record CambioEstado(EstadoSimuladores.ModoObraSocial obraSocial,
                               EstadoSimuladores.ModoAutorizacion autorizaciones,
                               EstadoSimuladores.ModoServicio pasarela,
                               EstadoSimuladores.ModoServicio videoconsulta,
                               Long demoraAutorizacionMs) {
    }
}
