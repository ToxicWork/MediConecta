package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.PagoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.integracion.pagos.PagosGateway;
import ar.edu.mediconecta.integracion.pagos.ResultadoCobro;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.modelo.EstadoPago;
import ar.edu.mediconecta.modelo.OrigenReintegro;
import ar.edu.mediconecta.modelo.Pago;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import ar.edu.mediconecta.negocio.excepciones.DatosDePagoInvalidosException;
import ar.edu.mediconecta.negocio.excepciones.PagoRechazadoException;
import ar.edu.mediconecta.negocio.excepciones.PasarelaNoDisponibleException;
import jakarta.ejb.SessionContext;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Cobro por la pasarela (con su compensación si la transacción no se confirma) y reintegro al cancelar. */
class ServicioDePagosBeanTest {

    private static final BigDecimal IMPORTE = new BigDecimal("9600.00");

    private final PagosGateway pasarela = mock(PagosGateway.class);
    private final PagoDAO pagoDAO = mock(PagoDAO.class);
    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final Validator validador = mock(Validator.class);
    private final TransactionSynchronizationRegistry transaccion = mock(TransactionSynchronizationRegistry.class);
    private final SessionContext contexto = mock(SessionContext.class);
    private final ServicioDeReintegros reintegros = mock(ServicioDeReintegros.class);
    private final Turno turno = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(1)), 10L);
    private ServicioDePagosBean servicio;
    private DatosPago tarjeta;

    @BeforeEach
    void armar() {
        servicio = new ServicioDePagosBean();
        Inyectar.en(servicio, "pasarela", pasarela);
        Inyectar.en(servicio, "pagoDAO", pagoDAO);
        Inyectar.en(servicio, "usuarioDAO", usuarioDAO);
        Inyectar.en(servicio, "validador", validador);
        Inyectar.en(servicio, "transaccion", transaccion);
        Inyectar.en(servicio, "contexto", contexto);
        Inyectar.en(servicio, "reintegros", reintegros);
        tarjeta = new DatosPago();
        tarjeta.setTitular("Juan Perez");
        tarjeta.setNumeroTarjeta("4111 1111 1111 1111");
        tarjeta.setVencimiento("12/29");
        tarjeta.setCodigoSeguridad("123");
        when(validador.validate(any(DatosPago.class))).thenReturn(Set.of());
        turno.reservar(Fixtures.paciente(7L, "OSDE"), LocalDateTime.now().plusMinutes(5)); // un pago es de un paciente
    }

    private static ResultadoCobro aprobado(String estado) {
        return new ResultadoCobro(true, "pay_1", estado, null, IMPORTE, "ARS", "VISA", "1111");
    }

    @Test
    void unCobroAprobadoGuardaElPagoYDejaListaLaCompensacion() {
        when(pasarela.cobrar(any())).thenReturn(aprobado("APROBADO"));

        Pago pago = servicio.cobrar(turno, IMPORTE, tarjeta);

        assertEquals("pay_1", pago.getIdPasarela());
        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        verify(pasarela).cobrar(argThat(p -> p.referencia().equals("turno-10") && p.importe().compareTo(IMPORTE) == 0
                && p.numeroTarjeta().equals("4111 1111 1111 1111") && p.moneda().equals("ARS")));
        verify(pagoDAO).guardar(pago);
        verify(transaccion).registerInterposedSynchronization(any(CompensacionDePago.class));
    }

    @Test
    void unCobroRechazadoNoGuardaNadaYDiceElMotivoEnCriollo() {
        when(pasarela.cobrar(any())).thenReturn(
                new ResultadoCobro(false, null, "RECHAZADO", "fondos_insuficientes", IMPORTE, "ARS", null, null));

        PagoRechazadoException error = assertThrows(PagoRechazadoException.class, () -> servicio.cobrar(turno, IMPORTE, tarjeta));

        assertEquals("Pago rechazado: La tarjeta no tiene fondos suficientes.", error.getMessage());
        verify(pagoDAO, never()).guardar(any());
        verify(transaccion, never()).registerInterposedSynchronization(any());
    }

    @Test
    void conLaPasarelaCaidaNoSeCobra() {
        when(pasarela.cobrar(any())).thenThrow(new IntegracionNoDisponibleException("circuito abierto", false));

        assertThrows(PasarelaNoDisponibleException.class, () -> servicio.cobrar(turno, IMPORTE, tarjeta));
        verify(pagoDAO, never()).guardar(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void conDatosDeTarjetaInvalidosNiSeLlamaALaPasarela() {
        ConstraintViolation<DatosPago> violacion = mock(ConstraintViolation.class);
        when(violacion.getMessage()).thenReturn("falta el código de seguridad");
        when(validador.validate(any(DatosPago.class))).thenReturn(Set.of(violacion));

        assertThrows(DatosDePagoInvalidosException.class, () -> servicio.cobrar(turno, IMPORTE, new DatosPago()));
        verify(pasarela, never()).cobrar(any());
    }

    @Test
    void elReintegroMarcaElPagoComoReembolsadoConSuMotivo() {
        Pago pago = new Pago(turno, IMPORTE, "ARS", "pay_1", "VISA", "1111");
        when(pagoDAO.buscarAprobadoDeTurno(10L)).thenReturn(Optional.of(pago));
        when(pasarela.reembolsar("pay_1", "Cancelación por el paciente", "turno-10")).thenReturn(aprobado("REEMBOLSADO"));

        servicio.reembolsarPagoDeTurno(10L, "Cancelación por el paciente");

        assertEquals(EstadoPago.REEMBOLSADO, pago.getEstado());
        assertEquals("Cancelación por el paciente", pago.getMotivoReembolso());
    }

    @Test
    void sinPagoAprobadoNoHayNadaQueReintegrar() {
        when(pagoDAO.buscarAprobadoDeTurno(10L)).thenReturn(Optional.empty());

        servicio.reembolsarPagoDeTurno(10L, "Cancelación");

        verify(pasarela, never()).reembolsar(anyString(), anyString(), anyString());
    }

    /** D-35 y D-41: si la pasarela no conoce el pago, no se marca reembolsado y queda para conciliar en Pagos. */
    @Test
    void siLaPasarelaNoConoceElPagoNoSeMarcaReembolsado() {
        Pago pago = new Pago(turno, IMPORTE, "ARS", "pay_1", "VISA", "1111");
        when(pagoDAO.buscarAprobadoDeTurno(10L)).thenReturn(Optional.of(pago));
        when(pasarela.reembolsar(eq("pay_1"), anyString(), anyString()))
                .thenReturn(new ResultadoCobro(false, "pay_1", ResultadoCobro.INEXISTENTE, null, null, null, null, null));

        servicio.reembolsarPagoDeTurno(10L, "Cancelación");

        assertEquals(EstadoPago.APROBADO, pago.getEstado());
        verify(reintegros).registrarParaConciliar(eq("pay_1"), eq("turno-10"), eq(10L), eq(IMPORTE), eq("Cancelación"),
                eq(OrigenReintegro.CANCELACION), argThat(d -> d.contains("no conoce")));
    }

    /** Con la pasarela caída el reintegro falla: el mensaje vuelve a la cola y, tras los reintentos, a la DLQ. */
    @Test
    void conLaPasarelaCaidaElReintegroFallaParaQueSeReintente() {
        when(pagoDAO.buscarAprobadoDeTurno(10L)).thenReturn(Optional.of(new Pago(turno, IMPORTE, "ARS", "pay_1", "VISA", "1111")));
        when(pasarela.reembolsar(anyString(), anyString(), anyString()))
                .thenThrow(new IntegracionNoDisponibleException("sin respuesta", true));

        assertThrows(PasarelaNoDisponibleException.class, () -> servicio.reembolsarPagoDeTurno(10L, "Cancelación"));
    }

    @Test
    void elPacienteVeSusPagosYElAdministrativoTodos() {
        Paciente juan = Fixtures.paciente(7L, "OSDE");
        List<Pago> deJuan = List.of(new Pago(turno, IMPORTE, "ARS", "pay_1", "VISA", "1111"));
        when(contexto.getCallerPrincipal()).thenReturn(juan::getEmail);
        when(usuarioDAO.buscarPorEmail(juan.getEmail())).thenReturn(Optional.of(juan));
        when(pagoDAO.buscarDePaciente(7L)).thenReturn(deJuan);
        when(pagoDAO.buscarTodos()).thenReturn(List.of());

        assertEquals(deJuan, servicio.pagosDelUsuarioActual());
        assertEquals(List.of(), servicio.todosLosPagos());

        when(usuarioDAO.buscarPorEmail(juan.getEmail())).thenReturn(Optional.empty());
        assertEquals(List.of(), servicio.pagosDelUsuarioActual());
    }

    // ---- Compensación ------------------------------------------------------------------------------

    private static final ResultadoCobro REEMBOLSADO =
            new ResultadoCobro(false, "pay_1", "REEMBOLSADO", null, null, null, null, null);

    private CompensacionDePago compensacion() {
        return new CompensacionDePago(pasarela, reintegros, "pay_1", "turno-10", 10L, IMPORTE);
    }

    @Test
    void siLaTransaccionSeConfirmaNoSeCompensa() {
        CompensacionDePago compensacion = compensacion();

        compensacion.beforeCompletion();
        compensacion.afterCompletion(Status.STATUS_COMMITTED);

        verify(pasarela, never()).reembolsarCompensacion(anyString(), anyString(), anyString());
    }

    /** Falla a mitad del flujo crítico: la base hace rollback y el cobro ya hecho se reembolsa. */
    @Test
    void siLaTransaccionSeRevierteSeReembolsaElCobro() {
        when(pasarela.reembolsarCompensacion(anyString(), anyString(), anyString())).thenReturn(REEMBOLSADO);

        compensacion().afterCompletion(Status.STATUS_ROLLEDBACK);

        verify(pasarela).reembolsarCompensacion(eq("pay_1"), argThat(m -> m.startsWith("Reversión automática")), eq("turno-10"));
        verifyNoInteractions(reintegros);
    }

    /** D-06: con la pasarela caída, el reintegro queda pendiente y se reintenta solo (antes, sólo en el log). */
    @Test
    void siLaPasarelaNoRespondeElReintegroQuedaPendiente() {
        when(pasarela.reembolsarCompensacion(anyString(), anyString(), anyString()))
                .thenThrow(new IntegracionNoDisponibleException("sin respuesta", true));

        compensacion().afterCompletion(Status.STATUS_ROLLEDBACK); // no propaga: no rompe el cierre de la transacción

        verify(reintegros).registrarParaReintentar(eq("pay_1"), eq("turno-10"), eq(10L), eq(IMPORTE),
                argThat(m -> m.startsWith("Reversión automática")), eq(OrigenReintegro.COMPENSACION), eq("sin respuesta"));
    }

    @Test
    void siLaPasarelaNoConoceElPagoDeLaCompensacionQuedaParaConciliar() {
        when(pasarela.reembolsarCompensacion(anyString(), anyString(), anyString()))
                .thenReturn(new ResultadoCobro(false, "pay_1", ResultadoCobro.INEXISTENTE, null, null, null, null, null));

        compensacion().afterCompletion(Status.STATUS_ROLLEDBACK);

        verify(reintegros).registrarParaConciliar(eq("pay_1"), eq("turno-10"), eq(10L), eq(IMPORTE), anyString(),
                eq(OrigenReintegro.COMPENSACION), eq("La pasarela no conoce el pago"));
        verify(reintegros, never()).registrarParaReintentar(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void siTampocoSePuedeRegistrarNoRompeElCierreDeLaTransaccion() {
        when(pasarela.reembolsarCompensacion(anyString(), anyString(), anyString()))
                .thenThrow(new IntegracionNoDisponibleException("sin respuesta", true));
        doThrow(new IllegalStateException("sin base")).when(reintegros)
                .registrarParaReintentar(any(), any(), any(), any(), any(), any(), any());

        compensacion().afterCompletion(Status.STATUS_ROLLEDBACK); // queda en el log para conciliar a mano

        verify(reintegros).registrarParaReintentar(any(), any(), any(), any(), any(), any(), any());
    }
}
