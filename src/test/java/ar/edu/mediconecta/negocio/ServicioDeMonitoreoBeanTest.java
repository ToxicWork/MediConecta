package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.EventoProcesadoDAO;
import ar.edu.mediconecta.dao.EventoPublicadoDAO;
import ar.edu.mediconecta.dao.LlamadaIntegracionDAO;
import ar.edu.mediconecta.dao.SolicitudAutorizacionDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.integracion.obrasocial.CoberturaGateway;
import ar.edu.mediconecta.integracion.pagos.PagosGateway;
import ar.edu.mediconecta.integracion.resiliencia.CircuitBreaker;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.integracion.videoconsulta.VideoconsultaGateway;
import ar.edu.mediconecta.mensajeria.Suscripciones;
import ar.edu.mediconecta.modelo.EventoProcesado;
import ar.edu.mediconecta.modelo.EventoPublicado;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.SolicitudAutorizacion;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.dto.EstadoSuscriptorDTO;
import ar.edu.mediconecta.negocio.dto.FilaAutorizacionDTO;
import ar.edu.mediconecta.negocio.dto.FilaMonitorDTO;
import jakarta.ejb.SessionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Monitor de eventos (HU-09): qué suscriptor procesó cada evento del usuario, con qué demora, y las autorizaciones. */
class ServicioDeMonitoreoBeanTest {

    private final UsuarioDAO usuarioDAO = mock(UsuarioDAO.class);
    private final EventoPublicadoDAO publicados = mock(EventoPublicadoDAO.class);
    private final EventoProcesadoDAO procesados = mock(EventoProcesadoDAO.class);
    private final SolicitudAutorizacionDAO solicitudes = mock(SolicitudAutorizacionDAO.class);
    private final LlamadaIntegracionDAO llamadas = mock(LlamadaIntegracionDAO.class);
    private final CoberturaGateway obraSocial = mock(CoberturaGateway.class);
    private final PagosGateway pasarela = mock(PagosGateway.class);
    private final VideoconsultaGateway video = mock(VideoconsultaGateway.class);
    private final SessionContext contexto = mock(SessionContext.class);
    private final Paciente paciente = Fixtures.paciente(7L, "OSDE");
    private ServicioDeMonitoreoBean monitor;

    @BeforeEach
    void armar() {
        monitor = new ServicioDeMonitoreoBean();
        Inyectar.en(monitor, "usuarioDAO", usuarioDAO);
        Inyectar.en(monitor, "eventoPublicadoDAO", publicados);
        Inyectar.en(monitor, "eventoProcesadoDAO", procesados);
        Inyectar.en(monitor, "solicitudDAO", solicitudes);
        Inyectar.en(monitor, "llamadaDAO", llamadas);
        Inyectar.en(monitor, "obraSocial", obraSocial);
        Inyectar.en(monitor, "pasarela", pasarela);
        Inyectar.en(monitor, "videoconsulta", video);
        Inyectar.en(monitor, "contexto", contexto);
        when(contexto.getCallerPrincipal()).thenReturn(paciente::getEmail);
        when(usuarioDAO.buscarPorEmail(paciente.getEmail())).thenReturn(Optional.of(paciente));
    }

    private static EstadoSuscriptorDTO de(FilaMonitorDTO fila, String suscriptor) {
        return fila.getSuscriptores().stream().filter(s -> s.getSuscriptor().equals(suscriptor)).findFirst().orElseThrow();
    }

    @Test
    void sinUsuarioIdentificadoNoHayNadaQueMostrar() {
        when(usuarioDAO.buscarPorEmail(paciente.getEmail())).thenReturn(Optional.empty());

        assertEquals(0, monitor.contarEventosDelUsuarioActual());
        assertEquals(List.of(), monitor.eventosDelUsuarioActual(0, 10));
        assertEquals(List.of(), monitor.autorizacionesRecientesDelUsuarioActual());
    }

    /** Cada suscriptor: si le corresponde el evento (selector), si ya lo procesó y cuánto tardó. */
    @Test
    void cadaEventoMuestraQueSuscriptorLoProcesoYCuantoTardo() {
        EventoPublicado confirmado = new EventoPublicado("a1b2c3d4-0000", "TURNO_CONFIRMADO", 10L, LocalDateTime.now().plusDays(1),
                7L, 20L, List.of(Suscripciones.NOTIFICACIONES, Suscripciones.AUDITORIA));
        when(publicados.buscarPaginaDeUsuario(7L, 0, 20)).thenReturn(List.of(confirmado));
        when(procesados.buscarPorClaves(anyCollection()))
                .thenReturn(List.of(new EventoProcesado(Suscripciones.NOTIFICACIONES, "a1b2c3d4-0000")));
        when(publicados.contarDeUsuario(7L)).thenReturn(1);

        List<FilaMonitorDTO> filas = monitor.eventosDelUsuarioActual(-3, 100); // se acota a la primera página de 20

        FilaMonitorDTO fila = filas.get(0);
        assertEquals("a1b2c3d4", fila.getIdCorto());
        assertEquals("Confirmado", fila.getEtiqueta());
        assertEquals("tag-success", fila.getEstilo());
        assertTrue(fila.isConfirmacion());
        assertTrue(de(fila, Suscripciones.NOTIFICACIONES).isProcesado());
        assertNotNull(de(fila, Suscripciones.NOTIFICACIONES).getLatenciaMs());
        assertTrue(de(fila, Suscripciones.AUDITORIA).isPendiente());
        assertFalse(de(fila, Suscripciones.FACTURACION).isAplica(), "Facturación sólo recibe autorizaciones y cancelaciones");
        assertFalse(fila.isCompleto());
        assertEquals(1, monitor.contarEventosDelUsuarioActual());
    }

    @Test
    void lasEtiquetasYLosEstilosDistinguenCadaTipoDeEvento() {
        FilaMonitorDTO cancelado = new FilaMonitorDTO("e1e1e1e1-x", "TURNO_CANCELADO", 1L, null, null, List.of());
        FilaMonitorDTO sala = new FilaMonitorDTO("e2e2e2e2-x", "SALA_ABIERTA", 1L, null, null, List.of());
        FilaMonitorDTO otro = new FilaMonitorDTO("e3e3e3e3-x", "OTRO", 1L, null, null, List.of());

        assertEquals("Cancelado", cancelado.getEtiqueta());
        assertEquals("tag-danger", cancelado.getEstilo());
        assertEquals("Sala de videoconsulta abierta", sala.getEtiqueta());
        assertEquals("tag-info", sala.getEstilo());
        assertEquals("OTRO", otro.getEtiqueta());
        assertTrue(otro.isCompleto(), "sin suscriptores pendientes");
    }

    /** Una autorización pendiente no tiene latencia; una resuelta, sí, y muestra cuántas llamadas fallaron. */
    @Test
    void lasAutorizacionesMuestranSuEstadoLasFallasYLaLatencia() {
        Turno turno = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(1)), 10L);
        turno.reservar(paciente, LocalDateTime.now().plusMinutes(5));
        SolicitudAutorizacion pendiente = new SolicitudAutorizacion(turno, "OSDE", "420101");
        SolicitudAutorizacion resuelta = new SolicitudAutorizacion(turno, "OSDE", "420101");
        resuelta.resolver(true, "AUT-9", "Prestación autorizada.", 2, "Thread-7");
        when(solicitudes.buscarRecientesDeUsuario(7L, 20)).thenReturn(List.of(pendiente, resuelta));
        when(llamadas.contarFallasPorReferencia(any())).thenReturn(Map.of(resuelta.getIdSolicitud(), 1L));

        List<FilaAutorizacionDTO> filas = monitor.autorizacionesRecientesDelUsuarioActual();

        assertEquals("PENDIENTE", filas.get(0).getEstado());
        assertNull(filas.get(0).getLatenciaMs());
        assertEquals(0L, filas.get(0).getFallas());
        assertEquals("AUT-9", filas.get(1).getCodigoAutorizacion());
        assertEquals(1L, filas.get(1).getFallas());
        assertEquals(2, filas.get(1).getIntento());
        assertNotNull(filas.get(1).getLatenciaMs());
        assertEquals(resuelta.getIdSolicitud().substring(0, 8), filas.get(1).getIdCorto());
    }

    @Test
    void losCircuitosSonLosTresDeLasIntegracionesYLasLlamadasLasUltimas25() {
        EstadoCircuito estado = new CircuitBreaker("x", 3, Duration.ofSeconds(30)).instantanea();
        when(obraSocial.estadoCircuito()).thenReturn(estado);
        when(pasarela.estadoCircuito()).thenReturn(estado);
        when(video.estadoCircuito()).thenReturn(estado);

        assertEquals(3, monitor.circuitos().size());
        monitor.llamadasRecientes();
        verify(llamadas).buscarRecientes(25);
    }
}
