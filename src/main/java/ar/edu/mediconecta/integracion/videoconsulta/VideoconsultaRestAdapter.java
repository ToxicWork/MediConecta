package ar.edu.mediconecta.integracion.videoconsulta;

import ar.edu.mediconecta.integracion.ConfiguracionIntegraciones;
import ar.edu.mediconecta.integracion.RegistroIntegraciones;
import ar.edu.mediconecta.integracion.SistemaExterno;
import ar.edu.mediconecta.integracion.resiliencia.CircuitBreaker;
import ar.edu.mediconecta.integracion.resiliencia.CircuitoAbiertoException;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Adapter hacia la API REST del proveedor de videoconsulta (JAX-RS Client, autenticación por X-Api-Key). */
@ApplicationScoped
public class VideoconsultaRestAdapter implements VideoconsultaGateway {

    @Inject
    private ConfiguracionIntegraciones config;

    @Inject
    private RegistroIntegraciones registro;

    private final CircuitBreaker circuito = new CircuitBreaker("Videoconsulta (REST)", 3, Duration.ofSeconds(30));
    private Client cliente;

    @PostConstruct
    void iniciar() {
        cliente = ClientBuilder.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build();
    }

    @PreDestroy
    void cerrar() {
        cliente.close();
    }

    @Override
    public SalaVideoconsulta crearSala(String referencia, String titulo, String profesional, String paciente) {
        long inicio = System.nanoTime();
        try {
            SalaVideoconsulta sala = circuito.ejecutar(() -> {
                try (Response respuesta = cliente.target(config.urlVideoconsulta()).path("salas")
                        .request(MediaType.APPLICATION_JSON)
                        .header("X-Api-Key", config.claveVideoconsulta())
                        .post(Entity.json(new PedidoSalaJson(referencia, titulo, profesional, paciente)))) {
                    if (respuesta.getStatus() != 201) {
                        throw new IntegracionNoDisponibleException("El proveedor de video respondió HTTP "
                                + respuesta.getStatus(), respuesta.getStatus() >= 500);
                    }
                    SalaJson json = respuesta.readEntity(SalaJson.class);
                    return new SalaVideoconsulta(json.id(), json.urlProfesional(), json.urlPaciente());
                } catch (ProcessingException sinRespuesta) {
                    throw new IntegracionNoDisponibleException("El proveedor de video no respondió", true, sinRespuesta);
                }
            });
            registro.registrar(SistemaExterno.VIDEOCONSULTA, "POST /salas", referencia, RegistroIntegraciones.OK,
                    "sala " + sala.id(), milis(inicio));
            return sala;
        } catch (IntegracionNoDisponibleException falla) {
            registro.registrar(SistemaExterno.VIDEOCONSULTA, "POST /salas", referencia,
                    falla instanceof CircuitoAbiertoException ? RegistroIntegraciones.CIRCUITO_ABIERTO : RegistroIntegraciones.ERROR,
                    falla.getMessage(), milis(inicio));
            throw falla;
        }
    }

    @Override
    public EstadoCircuito estadoCircuito() {
        return circuito.instantanea();
    }

    private static long milis(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    public record PedidoSalaJson(String referencia, String titulo, String profesional, String paciente) {
    }

    public record SalaJson(String id, String estado, String urlProfesional, String urlPaciente) {
    }
}
