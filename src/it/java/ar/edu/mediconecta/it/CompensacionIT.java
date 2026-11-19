package ar.edu.mediconecta.it;

import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Pago;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import jakarta.ejb.EJBException;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El flujo crítico con una falla después de cobrar y antes del commit, con la base, el broker y la pasarela reales.
 * Reemplaza a la "falla de demostración" que vivía en la aplicación (D-42): la falla la provoca la prueba, no el código
 * de producción.
 *
 * <ul>
 *   <li>Rollback y compensación: el turno sigue retenido, el pago no queda en la base y la pasarela figura con el cobro
 *       reembolsado.</li>
 *   <li>D-06: si en ese momento la pasarela no reembolsa (modo «sin reembolsos» del simulador), el reintegro queda
 *       pendiente en la base, y cuando la pasarela vuelve, reintentarlo lo devuelve.</li>
 * </ul>
 */
@ExtendWith(ArquillianExtension.class)
class CompensacionIT {

    private static final String EXTERNOS = "http://localhost:8080/externos/api/consola/";

    @Deployment
    static WebArchive despliegue() throws ClassNotFoundException {
        return Despliegues.conLaAplicacion("compensacion-it", CompensacionIT.class, ConfirmacionQueFalla.class,
                AdministrativoDePrueba.class, SistemaDePrueba.class);
    }

    @Inject
    private BaseDePruebas base;

    @Inject
    private ConfirmacionQueFalla confirmacion;

    @Inject
    private AdministrativoDePrueba administrativo;

    @Inject
    private SistemaDePrueba sistema;

    private String sufijo;
    private Long turnoId;

    @AfterEach
    void dejarTodoComoEstaba() throws Exception {
        pasarela("EN_LINEA");
        if (turnoId != null) {
            base.borrar(List.of("DELETE FROM pago WHERE turno_id = ?1"), turnoId);
            base.borrar(List.of("DELETE FROM reintegro_pendiente WHERE referencia = ?1",
                    "DELETE FROM llamada_integracion WHERE referencia = ?1"), "turno-" + turnoId);
        }
        if (sufijo != null) {
            base.borrar(List.of("DELETE FROM turno WHERE profesional_id IN (SELECT id FROM usuario WHERE email LIKE ?1)",
                    "DELETE FROM usuario WHERE email LIKE ?1"), "it.%." + sufijo + "@mediconecta.test");
        }
    }

    @Test
    void unaFallaAntesDelCommitRevierteTodoYDevuelveElCobro() throws Exception {
        Turno turno = turnoParaConfirmar();
        pasarela("EN_LINEA");

        assertThrows(EJBException.class, () -> confirmacion.confirmarYFallarAntesDelCommit(turno.getId(),
                turno.getPaciente().getId(), tarjeta()));

        assertEquals("HOLD", base.valor("SELECT estado FROM turno WHERE id = ?1", turnoId), "la confirmación se revirtió");
        assertEquals(0, base.contar("SELECT count(*) FROM pago WHERE turno_id = ?1", turnoId), "el pago no quedó");
        assertEquals("REEMBOLSADO", estadoEnLaPasarela(), "la compensación devolvió el cobro");
        assertEquals(1, base.contar("SELECT count(*) FROM llamada_integracion WHERE referencia = ?1 AND resultado = 'COMPENSACION'",
                "turno-" + turnoId), "y quedó registrada como compensación");
        assertEquals(0, base.contar("SELECT count(*) FROM reintegro_pendiente WHERE referencia = ?1", "turno-" + turnoId));
    }

    @Test
    void siLaPasarelaNoReembolsaElReintegroQuedaPendienteYSeHaceCuandoVuelve() throws Exception {
        Turno turno = turnoParaConfirmar();
        pasarela("SIN_REEMBOLSOS"); // cobra bien, pero responde 503 a los reembolsos

        assertThrows(EJBException.class, () -> confirmacion.confirmarYFallarAntesDelCommit(turno.getId(),
                turno.getPaciente().getId(), tarjeta()));

        assertEquals("APROBADO", estadoEnLaPasarela(), "la plata sigue cobrada en la pasarela");
        assertEquals("PENDIENTE", base.valor("SELECT estado FROM reintegro_pendiente WHERE referencia = ?1", "turno-" + turnoId),
                "D-06: el reintegro no se pierde, queda pendiente");
        Long reintegro = ((Number) base.valor("SELECT id FROM reintegro_pendiente WHERE referencia = ?1", "turno-" + turnoId)).longValue();

        pasarela("EN_LINEA");
        assertTrue(administrativo.reintentarAhora(reintegro).startsWith("Listo"));

        assertEquals("REINTEGRADO", base.valor("SELECT estado FROM reintegro_pendiente WHERE id = ?1", reintegro));
        assertEquals("REEMBOLSADO", estadoEnLaPasarela(), "la pasarela devolvió el cobro");
    }

    /** D-41: la pasarela no conoce el pago de un turno cancelado: ya no queda sólo en el log, queda para conciliar. */
    @Test
    void siLaPasarelaNoConoceElPagoAlCancelarQuedaParaConciliacionManual() throws Exception {
        sufijo = BaseDePruebas.sufijo();
        turnoId = base.enTransaccion(() -> {
            Turno turno = base.turnoConfirmado(base.profesional(sufijo), base.pacienteParticular(sufijo),
                    LocalDateTime.now().plusDays(93).withNano(0));
            base.em().flush();
            base.em().persist(new Pago(turno, new BigDecimal("32000"), "ARS", "pay_desconocido_" + sufijo, "VISA", "1111"));
            return turno.getId();
        });

        sistema.reintegrarPagoDeTurno(turnoId);

        assertEquals("CONCILIACION_MANUAL", base.valor("SELECT estado FROM reintegro_pendiente WHERE referencia = ?1", "turno-" + turnoId));
        assertEquals("APROBADO", base.valor("SELECT estado FROM pago WHERE turno_id = ?1", turnoId),
                "no se marca reembolsado algo que la pasarela no devolvió");
    }

    private Turno turnoParaConfirmar() throws Exception {
        sufijo = BaseDePruebas.sufijo();
        Turno turno = base.enTransaccion(() -> {
            Profesional profesional = base.profesional(sufijo);
            Paciente particular = base.pacienteParticular(sufijo);
            return base.turnoEnHold(profesional, particular, LocalDateTime.now().plusDays(92).withNano(0));
        });
        turnoId = turno.getId();
        return turno;
    }

    private static DatosPago tarjeta() {
        DatosPago datos = new DatosPago();
        datos.setTitular("Paciente Integracion");
        datos.setNumeroTarjeta("4111 1111 1111 1111");
        datos.setVencimiento("12/29");
        datos.setCodigoSeguridad("123");
        return datos;
    }

    private static void pasarela(String modo) {
        Client cliente = ClientBuilder.newClient();
        try {
            cliente.target(EXTERNOS + "estado").request().put(Entity.json("{\"pasarela\":\"" + modo + "\"}")).close();
        } finally {
            cliente.close();
        }
    }

    /** El estado del cobro de este turno en la pasarela simulada (la consola lista todos los pagos que recibió). */
    private String estadoEnLaPasarela() {
        Client cliente = ClientBuilder.newClient();
        try (JsonReader lector = Json.createReader(new StringReader(cliente.target(EXTERNOS + "pagos").request().get(String.class)))) {
            String estado = lector.readArray().stream().map(JsonObject.class::cast)
                    .filter(p -> ("turno-" + turnoId).equals(p.getString("referencia", null)))
                    .map(p -> p.getString("estado", null)).reduce((primero, ultimo) -> ultimo).orElse(null);
            assertNotNull(estado, "la pasarela recibió el cobro del turno " + turnoId);
            return estado;
        } finally {
            cliente.close();
        }
    }
}
