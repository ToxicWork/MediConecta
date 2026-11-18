package ar.edu.mediconecta.modelo;

import ar.edu.mediconecta.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglas de las entidades que registran lo que pasó: pagos, reclamos, llamadas a integraciones, avisos y auditoría. */
class RegistrosDelDominioTest {

    private static Turno confirmadoConObraSocial() {
        Turno turno = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(1)), 10L);
        turno.reservar(Fixtures.paciente(7L, "OSDE"), LocalDateTime.now().plusMinutes(5));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE", "210", "420101", new BigDecimal("32000"),
                new BigDecimal("9600"), new BigDecimal("22400"), EstadoAutorizacion.PENDIENTE));
        turno.registrarAutorizacion(true, "AUT-77", "Prestación autorizada.");
        return turno;
    }

    @Test
    void unPagoEsDelPacienteYDelProfesionalDelTurnoYEmpiezaAprobado() {
        Pago pago = new Pago(confirmadoConObraSocial(), new BigDecimal("9600"), "ARS", "pay_1", "VISA", "1111");

        assertEquals(7L, pago.getPacienteId());
        assertEquals(20L, pago.getProfesionalId());
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        assertNull(pago.getReembolsadoEn());
    }

    /** Reembolsar dos veces no cambia nada: queda el primer motivo y la primera fecha. */
    @Test
    void elReembolsoSeAplicaUnaSolaVez() {
        Pago pago = new Pago(confirmadoConObraSocial(), new BigDecimal("9600"), "ARS", "pay_1", "VISA", "1111");

        pago.marcarReembolsado("Cancelación por el paciente");
        LocalDateTime primero = pago.getReembolsadoEn();
        pago.marcarReembolsado("Otro motivo");

        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstado());
        assertEquals("Cancelación por el paciente", pago.getMotivoReembolso());
        assertEquals(primero, pago.getReembolsadoEn());
    }

    @Test
    void elReclamoCopiaDelTurnoLaObraSocialLaPrestacionYLaAutorizacion() {
        ReclamoObraSocial reclamo = new ReclamoObraSocial(confirmadoConObraSocial(), new BigDecimal("22400"));

        assertEquals("OSDE", reclamo.getObraSocial());
        assertEquals("420101", reclamo.getCodigoPrestacion());
        assertEquals("AUT-77", reclamo.getCodigoAutorizacion());
        assertEquals(20L, reclamo.getProfesionalId());
        assertEquals(EstadoReclamo.PENDIENTE_PRESENTACION, reclamo.getEstado());
        assertNull(reclamo.getAnuladoEn());

        reclamo.anular();
        assertEquals(EstadoReclamo.ANULADO, reclamo.getEstado());
        assertNotNull(reclamo.getAnuladoEn());
    }

    /** El detalle de una llamada se guarda hasta 400 caracteres (la columna): uno más largo se recorta. */
    @Test
    void elDetalleDeUnaLlamadaSeRecortaA400Caracteres() {
        String justo = "x".repeat(400);

        assertEquals(justo, new LlamadaIntegracion("Pasarela (REST)", "POST /pagos", "turno-1", "OK", justo, 18).getDetalle());
        assertEquals(400, new LlamadaIntegracion("Pasarela (REST)", "POST /pagos", "turno-1", "OK", justo + "y", 18)
                .getDetalle().length());
        assertNull(new LlamadaIntegracion("Pasarela (REST)", "POST /pagos", "turno-1", "OK", null, 18).getDetalle());
    }

    @Test
    void unaLlamadaGuardaSistemaOperacionResultadoYLatencia() {
        LlamadaIntegracion llamada = new LlamadaIntegracion("Obra social (SOAP)", "validarCobertura", "turno-9", "RECHAZO",
                "SOAP Fault de negocio", 42);

        assertEquals("Obra social (SOAP)", llamada.getSistema());
        assertEquals("validarCobertura", llamada.getOperacion());
        assertEquals("turno-9", llamada.getReferencia());
        assertEquals("RECHAZO", llamada.getResultado());
        assertEquals(42, llamada.getLatenciaMs());
        assertNotNull(llamada.getOcurridaEn());
    }

    @Test
    void unPedidoIdempotenteSoloSirveParaSuOperacion() {
        PedidoIdempotente pedido = new PedidoIdempotente(7L, "clave-1", "POST /api/v1/turnos/10/cancelacion", 200, "{}",
                "\"3\"", LocalDateTime.now());

        assertTrue(pedido.esDeLaOperacion("POST /api/v1/turnos/10/cancelacion"));
        assertFalse(pedido.esDeLaOperacion("POST /api/v1/turnos/11/cancelacion"));
        assertEquals(7L, pedido.getUsuarioId());
        assertEquals("clave-1", pedido.getClave());
        assertEquals(200, pedido.getEstadoHttp());
        assertEquals("{}", pedido.getRespuesta());
        assertEquals("\"3\"", pedido.getEtag());
    }

    @Test
    void unAvisoEmpiezaSinLeerYSeMarcaLeido() {
        Notificacion aviso = new Notificacion(7L, "juan@test", CanalNotificacion.EMAIL, "Turno confirmado", "Tu turno...", "ev-1");

        assertFalse(aviso.isLeida());
        aviso.marcarLeida();
        assertTrue(aviso.isLeida());
        assertEquals("ev-1", aviso.getIdEvento());
        assertEquals(7L, aviso.getDestinatarioId());
    }

    @Test
    void laAuditoriaGuardaElEventoYCuandoSeRegistro() {
        LocalDateTime ocurrido = LocalDateTime.of(2026, 11, 18, 10, 0);
        RegistroAuditoria registro = new RegistroAuditoria("ev-1", "TURNO_CANCELADO", 10L, 7L, 20L, ocurrido);

        assertEquals("TURNO_CANCELADO", registro.getTipoEvento());
        assertEquals(10L, registro.getTurnoId());
        assertEquals(ocurrido, registro.getOcurridoEn());
        assertTrue(registro.getRegistradoEn().isAfter(ocurrido));
    }
}
