package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.ReclamoObraSocialDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.EstadoReclamo;
import ar.edu.mediconecta.modelo.ReclamoObraSocial;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Turno;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Facturación (suscriptor del tópico con selector): reclamo a la obra social cuando se autoriza el turno, y al cancelar,
 * reclamo anulado y reintegro. Un mensaje repetido no genera un segundo reclamo (consumidor idempotente, Eje C).
 */
class ServicioDeFacturacionBeanTest {

    private static final BigDecimal A_CARGO_DE_LA_OBRA_SOCIAL = new BigDecimal("22400");

    private final ReclamoObraSocialDAO reclamos = mock(ReclamoObraSocialDAO.class);
    private final TurnoDAO turnoDAO = mock(TurnoDAO.class);
    private final ServicioDePagos pagos = mock(ServicioDePagos.class);
    private ServicioDeFacturacionBean facturacion;
    private Turno turno;

    @BeforeEach
    void armar() {
        facturacion = new ServicioDeFacturacionBean();
        Inyectar.en(facturacion, "reclamoDAO", reclamos);
        Inyectar.en(facturacion, "turnoDAO", turnoDAO);
        Inyectar.en(facturacion, "pagos", pagos);
        turno = conImporteDeLaObraSocial(A_CARGO_DE_LA_OBRA_SOCIAL);
    }

    private Turno conImporteDeLaObraSocial(BigDecimal importe) {
        Turno nuevo = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(1)), 10L);
        nuevo.reservar(Fixtures.paciente(7L, "OSDE"), LocalDateTime.now().plusMinutes(5));
        nuevo.confirmar(new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE", "210", "420101", new BigDecimal("32000"),
                new BigDecimal("32000").subtract(importe), importe, EstadoAutorizacion.PENDIENTE));
        when(turnoDAO.buscarPorId(10L)).thenReturn(nuevo);
        return nuevo;
    }

    private EventoTurno evento(TipoEventoTurno tipo) {
        return EventoTurno.builder().tipo(tipo).turno(turno).build();
    }

    @Test
    void alAutorizarseElTurnoSeReclamaALaObraSocialSuParte() {
        when(reclamos.buscarVigenteDeTurno(10L)).thenReturn(Optional.empty());

        facturacion.procesarEvento(evento(TipoEventoTurno.TURNO_AUTORIZADO));

        verify(reclamos).guardar(argThat(r -> r.getImporte().compareTo(A_CARGO_DE_LA_OBRA_SOCIAL) == 0
                && r.getEstado() == EstadoReclamo.PENDIENTE_PRESENTACION));
    }

    /** Eje C, mensaje duplicado: si ya hay un reclamo vigente, la segunda entrega del evento no genera otro. */
    @Test
    void unaAutorizacionRepetidaNoGeneraUnSegundoReclamo() {
        when(reclamos.buscarVigenteDeTurno(10L)).thenReturn(Optional.of(new ReclamoObraSocial(turno, A_CARGO_DE_LA_OBRA_SOCIAL)));

        facturacion.procesarEvento(evento(TipoEventoTurno.TURNO_AUTORIZADO));

        verify(reclamos, never()).guardar(any());
    }

    @Test
    void sinImporteACargoDeLaObraSocialNoHayNadaQueReclamar() {
        turno = conImporteDeLaObraSocial(BigDecimal.ZERO);

        facturacion.procesarEvento(evento(TipoEventoTurno.TURNO_AUTORIZADO));

        verify(reclamos, never()).guardar(any());
    }

    @Test
    void alCancelarseSeAnulaElReclamoYSeReintegraConElMotivoDeQuienCancelo() {
        ReclamoObraSocial vigente = new ReclamoObraSocial(turno, A_CARGO_DE_LA_OBRA_SOCIAL);
        when(reclamos.buscarVigenteDeTurno(10L)).thenReturn(Optional.of(vigente));
        turno.cancelar(Rol.PROFESIONAL, LocalDateTime.now());

        facturacion.procesarEvento(evento(TipoEventoTurno.TURNO_CANCELADO));

        assertEquals(EstadoReclamo.ANULADO, vigente.getEstado());
        verify(pagos).reembolsarPagoDeTurno(10L, "Cancelación del turno por el profesional");
    }

    @Test
    void unTurnoQueYaNoExisteOUnEventoQueNoLeCorrespondeSeIgnoran() {
        when(turnoDAO.buscarPorId(10L)).thenReturn(null);
        facturacion.procesarEvento(evento(TipoEventoTurno.TURNO_CANCELADO));

        when(turnoDAO.buscarPorId(10L)).thenReturn(turno);
        facturacion.procesarEvento(evento(TipoEventoTurno.TURNO_CONFIRMADO)); // el selector no debería dejarlo pasar

        verify(reclamos, never()).guardar(any());
        verify(pagos, never()).reembolsarPagoDeTurno(anyLong(), anyString());
    }

    @Test
    void elAdministrativoVeTodosLosReclamos() {
        List<ReclamoObraSocial> todos = List.of(new ReclamoObraSocial(turno, A_CARGO_DE_LA_OBRA_SOCIAL));
        when(reclamos.buscarTodos()).thenReturn(todos);

        assertEquals(todos, facturacion.todosLosReclamos());
    }
}
