package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.PagoDAO;
import ar.edu.mediconecta.dao.ReintegroPendienteDAO;
import ar.edu.mediconecta.integracion.pagos.PagosGateway;
import ar.edu.mediconecta.integracion.pagos.ResultadoCobro;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.EstadoPago;
import ar.edu.mediconecta.modelo.EstadoReintegro;
import ar.edu.mediconecta.modelo.OrigenReintegro;
import ar.edu.mediconecta.modelo.Pago;
import ar.edu.mediconecta.modelo.ReintegroPendiente;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.ejb.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-06 y D-41: los reintegros que la pasarela no pudo hacer se guardan, se reintentan solos y los ve el administrativo. */
class ServicioDeReintegrosBeanTest {

    private static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 19, 20, 0);
    private static final ResultadoCobro REEMBOLSADO = new ResultadoCobro(false, "pay_1", "REEMBOLSADO", null, null, null, null, null);

    private final ReintegroPendienteDAO dao = mock(ReintegroPendienteDAO.class);
    private final PagoDAO pagoDAO = mock(PagoDAO.class);
    private final PagosGateway pasarela = mock(PagosGateway.class);
    private final SessionContext contexto = mock(SessionContext.class);
    private final ServicioDeReintegrosBean servicio = new ServicioDeReintegrosBean();

    @BeforeEach
    void armar() {
        Inyectar.en(servicio, "reintegros", dao);
        Inyectar.en(servicio, "pagoDAO", pagoDAO);
        Inyectar.en(servicio, "pasarela", pasarela);
        Inyectar.en(servicio, "contexto", contexto);
        Inyectar.en(servicio, "reloj", Clock.fixed(AHORA.atZone(ZONA).toInstant(), ZONA));
    }

    private ReintegroPendiente guardado(OrigenReintegro origen) {
        ReintegroPendiente r = ReintegroPendiente.paraReintentar("pay_1", "turno-10", 10L, new BigDecimal("32000"),
                "Cancelación del turno por el paciente", origen, "503", AHORA.minusMinutes(5));
        when(dao.buscarParaActualizar(1L)).thenReturn(r);
        return r;
    }

    @Test
    void registraElPendienteUnaSolaVezPorPago() {
        servicio.registrarParaReintentar("pay_1", "turno-10", 10L, BigDecimal.TEN, "m", OrigenReintegro.COMPENSACION, "503");
        ArgumentCaptor<ReintegroPendiente> guardado = ArgumentCaptor.forClass(ReintegroPendiente.class);
        verify(dao).guardar(guardado.capture());
        assertEquals(EstadoReintegro.PENDIENTE, guardado.getValue().getEstado());
        assertEquals(AHORA.plusMinutes(1), guardado.getValue().getProximoIntento());

        when(dao.existeAbiertoDePago("pay_2")).thenReturn(true);
        servicio.registrarParaReintentar("pay_2", "turno-11", 11L, BigDecimal.TEN, "m", OrigenReintegro.COMPENSACION, "503");
        servicio.registrarParaConciliar("pay_2", "turno-11", 11L, BigDecimal.TEN, "m", OrigenReintegro.CANCELACION, "no lo conoce");
        verify(dao).guardar(any()); // sólo el primero
    }

    @Test
    void registraParaConciliarLoQueLaPasarelaNoConoce() {
        servicio.registrarParaConciliar("pay_3", "turno-12", 12L, null, "m", OrigenReintegro.CANCELACION, "no lo conoce");

        ArgumentCaptor<ReintegroPendiente> guardado = ArgumentCaptor.forClass(ReintegroPendiente.class);
        verify(dao).guardar(guardado.capture());
        assertEquals(EstadoReintegro.CONCILIACION_MANUAL, guardado.getValue().getEstado());
    }

    @Test
    void elTemporizadorReintentaLosVencidosYSiLaPasarelaVolvioQuedaReintegrado() {
        ReintegroPendiente r = guardado(OrigenReintegro.COMPENSACION);
        Inyectar.en(r, "id", 1L);
        when(dao.buscarVencidos(AHORA)).thenReturn(List.of(r));
        when(pasarela.reembolsarCompensacion("pay_1", r.getMotivo(), "turno-10")).thenReturn(REEMBOLSADO);

        assertEquals(1, servicio.reintentarVencidos());

        assertEquals(EstadoReintegro.REINTEGRADO, r.getEstado());
        verify(pasarela, never()).reembolsar(anyString(), anyString(), anyString());
    }

    @Test
    void elReintegroDeUnaCancelacionMarcaElPagoComoReembolsado() {
        ReintegroPendiente r = guardado(OrigenReintegro.CANCELACION);
        Turno turno = Fixtures.conId(Fixtures.turno(AHORA.plusDays(3)), 10L);
        turno.reservar(Fixtures.paciente(7L, "OSDE"), AHORA.plusMinutes(5));
        Pago pago = new Pago(turno, new BigDecimal("32000"), "ARS", "pay_1", "VISA", "1111");
        when(pagoDAO.buscarAprobadoDeTurno(10L)).thenReturn(Optional.of(pago));
        when(pasarela.reembolsar("pay_1", r.getMotivo(), "turno-10")).thenReturn(REEMBOLSADO);

        assertEquals("Listo: la pasarela hizo el reintegro.", servicio.reintentarAhora(1L));

        assertEquals(EstadoReintegro.REINTEGRADO, r.getEstado());
        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstado());
    }

    @Test
    void siLaPasarelaSigueCaidaEsperaMasYSiNoConoceElPagoPasaAConciliacion() {
        ReintegroPendiente r = guardado(OrigenReintegro.COMPENSACION);
        when(pasarela.reembolsarCompensacion(anyString(), anyString(), anyString()))
                .thenThrow(new IntegracionNoDisponibleException("La pasarela respondió HTTP 503", true));

        assertTrue(servicio.reintentarAhora(1L).contains("sigue sin responder"));
        assertEquals(EstadoReintegro.PENDIENTE, r.getEstado());
        assertEquals(2, r.getIntentos());
        assertEquals(AHORA.plusMinutes(2), r.getProximoIntento());

        ReintegroPendiente otro = guardado(OrigenReintegro.CANCELACION);
        when(pasarela.reembolsar(anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoCobro(false, null, ResultadoCobro.INEXISTENTE, "pago_inexistente", null, null, null, null));
        assertTrue(servicio.reintentarAhora(1L).contains("no conoce el pago"));
        assertEquals(EstadoReintegro.CONCILIACION_MANUAL, otro.getEstado());

        ReintegroPendiente rechazado = guardado(OrigenReintegro.CANCELACION);
        when(pasarela.reembolsar(anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoCobro(false, null, "RECHAZADO", "monto_invalido", null, null, null, null));
        assertTrue(servicio.reintentarAhora(1L).contains("rechazó"));
        assertEquals(EstadoReintegro.CONCILIACION_MANUAL, rechazado.getEstado());
    }

    @Test
    void elAdministrativoLoMarcaConciliadoConSuNombreYNoSeTocaDosVeces() {
        ReintegroPendiente r = guardado(OrigenReintegro.CANCELACION);
        when(contexto.getCallerPrincipal()).thenReturn(() -> "carla.admin@mediconecta.test");

        servicio.marcarConciliado(1L);

        assertEquals(EstadoReintegro.CONCILIADO, r.getEstado());
        assertEquals("carla.admin@mediconecta.test", r.getResueltoPor());
        assertThrows(ErrorDeNegocioException.class, () -> servicio.marcarConciliado(1L));
        assertThrows(ErrorDeNegocioException.class, () -> servicio.reintentarAhora(1L));
        assertThrows(ErrorDeNegocioException.class, () -> servicio.reintentarAhora(null));
        assertThrows(ErrorDeNegocioException.class, () -> servicio.marcarConciliado(99L));
    }

    @Test
    void listaLosAbiertosParaElAdministrativo() {
        List<ReintegroPendiente> abiertos = List.of(guardado(OrigenReintegro.CANCELACION));
        when(dao.buscarAbiertos()).thenReturn(abiertos);

        assertEquals(abiertos, servicio.abiertos());
    }

    @Test
    void unVencidoQueYaSeResolvioEnElMedioNoSeReintenta() {
        ReintegroPendiente r = guardado(OrigenReintegro.COMPENSACION);
        Inyectar.en(r, "id", 1L);
        r.marcarReintegrado(AHORA);
        when(dao.buscarVencidos(AHORA)).thenReturn(List.of(r));

        servicio.reintentarVencidos();

        verify(pasarela, never()).reembolsarCompensacion(anyString(), anyString(), anyString());
    }
}
