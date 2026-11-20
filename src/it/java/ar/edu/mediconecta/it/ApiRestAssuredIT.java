package ar.edu.mediconecta.it;

import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import jakarta.inject.Inject;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.RunAsClient;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la API REST con REST Assured (criterios de la Clase 15, eje B: "colección de API versionada, corrida en
 * CI: un caso de éxito y uno de error por endpoint"). Cada endpoint del OpenAPI tiene al menos un caso de éxito y uno
 * de error, pedidos desde afuera, por HTTPS y con el JWT, como lo haría un sistema externo.
 *
 * <p>El primer paso corre adentro del servidor (Arquillian): deja un paciente de prueba con un turno confirmado, porque
 * la API no reserva turnos. Los demás corren del lado del cliente ({@code @RunAsClient}). El paciente se reutiliza en
 * cada corrida y el turno de cada corrida termina cancelado por la propia prueba.
 */
@ExtendWith(ArquillianExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiRestAssuredIT {

    private static final String PACIENTE = "it.paciente.api@mediconecta.test";
    private static final String CLAVE = "Prueba-API-2026"; // de prueba: el paciente sólo existe para esta suite
    private static final String ADMINISTRATIVO = "carla.admin@mediconecta.test";
    private static final String CLAVE_DEMO = System.getProperty("mediconecta.demo.clave", "demo1234");
    private static final long INEXISTENTE = 999_999_999L;

    @Deployment
    static WebArchive despliegue() throws ClassNotFoundException {
        return Despliegues.conLaAplicacion("api-rest-assured-it", ApiRestAssuredIT.class, RespuestaApi.class);
    }

    @Inject
    private BaseDePruebas base;

    @Inject
    private ServicioDeUsuarios usuarios;

    // ---- Preparación, adentro del servidor ---------------------------------------------------------------

    @Test
    @Order(0)
    void prepararUnPacienteConUnTurnoConfirmado() throws Exception {
        if (usuarios.buscarPorEmail(PACIENTE).isEmpty()) {
            usuarios.registrarPaciente(new DatosRegistro("Paciente API", PACIENTE, null, null, null, null), CLAVE);
        }
        base.enTransaccion(() -> {
            Paciente paciente = (Paciente) base.em().createQuery("SELECT u FROM Usuario u WHERE u.email = :e", Usuario.class)
                    .setParameter("e", PACIENTE).getSingleResult();
            Profesional profesional = base.em().createQuery("SELECT p FROM Profesional p WHERE p.email = :e", Profesional.class)
                    .setParameter("e", "it.profesional.api@mediconecta.test").getResultStream().findFirst()
                    .orElseGet(() -> base.profesional("api"));
            LocalDateTime fecha = LocalDateTime.now().plusDays(94).withHour(8).withMinute(0).withSecond(0).withNano(0)
                    .plusMinutes(5L * ThreadLocalRandom.current().nextInt(0, 140));
            return base.turnoConfirmado(profesional, paciente, fecha);
        });
    }

    // ---- POST /auth/login y POST /auth/logout --------------------------------------------------------------

    @Test
    @Order(1)
    @RunAsClient
    void ingresarDevuelveElTokenYLosErroresSonLosDocumentados() {
        RespuestaApi ok = ClienteApi.pedir("POST", "/auth/login", Map.of("email", PACIENTE, "password", CLAVE), null, null);
        assertEquals(200, ok.estado());
        assertNotNull(ClienteApi.campo(ok.cuerpo(), "token"));

        assertEquals(401, ClienteApi.pedir("POST", "/auth/login",
                Map.of("email", "no.existe@mediconecta.test", "password", "x"), null, null).estado(), "credenciales inválidas");
        assertEquals(400, ClienteApi.pedir("POST", "/auth/login", null, null, null).estado(), "sin cuerpo");
        assertEquals(422, ClienteApi.pedir("POST", "/auth/login", Map.of("email", PACIENTE), null, null).estado(), "sin contraseña");
        assertEquals(415, ClienteApi.pedir("POST", "/auth/login", "email=x&password=y", "text/plain", null).estado(),
                "un cuerpo que no es JSON");
    }

    @Test
    @Order(2)
    @RunAsClient
    void cerrarSesionRevocaElToken() {
        String token = ClienteApi.token(PACIENTE, CLAVE);

        assertEquals(204, ClienteApi.pedir("POST", "/auth/logout", null, null, ClienteApi.bearer(token)).estado());
        assertEquals(401, ClienteApi.pedir("GET", "/turnos", null, null, ClienteApi.bearer(token)).estado(),
                "ADR-0019: el token revocado ya no sirve");
        assertEquals(401, ClienteApi.pedir("POST", "/auth/logout", null, null, null).estado(), "sin token");
    }

    // ---- GET /profesionales y GET /profesionales/{id}/disponibilidad ---------------------------------------

    @Test
    @Order(3)
    @RunAsClient
    void losProfesionalesYSuDisponibilidadSonPublicos() {
        RespuestaApi profesionales = ClienteApi.pedir("GET", "/profesionales", null, null, null);
        assertEquals(200, profesionales.estado());
        String id = ClienteApi.campo(profesionales.cuerpo(), "find { it.nombre.contains('Ana Lopez') }.id");
        assertNotNull(id, "la profesional de demo está en la lista");
        assertEquals(406, ClienteApi.pedir("GET", "/profesionales", null, null, Map.of("Accept", "application/xml")).estado(),
                "sólo responde JSON");

        assertEquals(200, ClienteApi.pedir("GET", "/profesionales/" + id + "/disponibilidad", null, null, null).estado());
        assertEquals(404, ClienteApi.pedir("GET", "/profesionales/" + INEXISTENTE + "/disponibilidad", null, null, null).estado());
    }

    // ---- GET /turnos, DELETE /turnos y GET /turnos/{id} ----------------------------------------------------

    @Test
    @Order(4)
    @RunAsClient
    void misTurnosSonDelPacienteYDeNadieMas() {
        String token = ClienteApi.token(PACIENTE, CLAVE);
        RespuestaApi turnos = ClienteApi.pedir("GET", "/turnos", null, null, ClienteApi.bearer(token));
        assertEquals(200, turnos.estado());
        assertTrue(turnos.cuerpo().contains("CONFIRMADO"), "está el turno que preparó la prueba");

        assertEquals(401, ClienteApi.pedir("GET", "/turnos", null, null, null).estado(), "sin token");
        assertEquals(403, ClienteApi.pedir("GET", "/turnos", null, null,
                ClienteApi.bearer(ClienteApi.token(ADMINISTRATIVO, CLAVE_DEMO))).estado(), "el administrativo no tiene turnos propios");
        assertEquals(405, ClienteApi.pedir("DELETE", "/turnos", null, null, ClienteApi.bearer(token)).estado(),
                "no se borran todos los turnos");
    }

    @Test
    @Order(5)
    @RunAsClient
    void unTurnoConSuEtagYSinCambiosDevuelve304() {
        String token = ClienteApi.token(PACIENTE, CLAVE);
        long turno = ClienteApi.turnoConfirmado(token);

        RespuestaApi leido = ClienteApi.pedir("GET", "/turnos/" + turno, null, null, ClienteApi.bearer(token));
        assertEquals(200, leido.estado());
        assertEquals(String.valueOf(turno), ClienteApi.campo(leido.cuerpo(), "id"));
        String etag = leido.cabecera("ETag");
        assertNotNull(etag, "Eje C: el turno trae su versión");

        Map<String, String> conEtag = new HashMap<>(ClienteApi.bearer(token));
        conEtag.put("If-None-Match", etag);
        assertEquals(304, ClienteApi.pedir("GET", "/turnos/" + turno, null, null, conEtag).estado());
        assertEquals(404, ClienteApi.pedir("GET", "/turnos/" + INEXISTENTE, null, null, ClienteApi.bearer(token)).estado());
    }

    // ---- POST /turnos/{id}/cancelacion ---------------------------------------------------------------------

    @Test
    @Order(6)
    @RunAsClient
    void cancelarRespetaLaVersionYNoSeCancelaDosVeces() {
        String token = ClienteApi.token(PACIENTE, CLAVE);
        long turno = ClienteApi.turnoConfirmado(token);
        String etag = ClienteApi.pedir("GET", "/turnos/" + turno, null, null, ClienteApi.bearer(token)).cabecera("ETag");
        String ruta = "/turnos/" + turno + "/cancelacion";

        Map<String, String> versionVieja = new HashMap<>(ClienteApi.bearer(token));
        versionVieja.put("If-Match", "\"-1\"");
        assertEquals(412, ClienteApi.pedir("POST", ruta, null, null, versionVieja).estado(), "el turno cambió desde que se leyó");

        Map<String, String> versionActual = new HashMap<>(ClienteApi.bearer(token));
        versionActual.put("If-Match", etag);
        RespuestaApi cancelado = ClienteApi.pedir("POST", ruta, null, null, versionActual);
        assertEquals(200, cancelado.estado());
        assertEquals("CANCELADO", ClienteApi.campo(cancelado.cuerpo(), "estado"));

        assertEquals(409, ClienteApi.pedir("POST", ruta, null, null, ClienteApi.bearer(token)).estado(), "ya estaba cancelado");
        assertEquals(404, ClienteApi.pedir("POST", "/turnos/" + INEXISTENTE + "/cancelacion", null, null,
                ClienteApi.bearer(token)).estado());
        assertEquals(401, ClienteApi.pedir("POST", ruta, null, null, null).estado(), "sin token");
    }
}
