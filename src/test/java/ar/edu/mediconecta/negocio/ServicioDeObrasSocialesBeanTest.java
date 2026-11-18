package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.integracion.obrasocial.CoberturaGateway;
import ar.edu.mediconecta.integracion.obrasocial.OrigenCobertura;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoAutorizacion;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.integracion.resiliencia.CircuitBreaker;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.mensajeria.MensajeAutorizacion;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Prestacion;
import ar.edu.mediconecta.negocio.excepciones.ObraSocialNoDisponibleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** La obra social, vista desde el negocio: sin obra social no se consulta; caída, la autorización se reintenta. */
class ServicioDeObrasSocialesBeanTest {

    private final CoberturaGateway gateway = mock(CoberturaGateway.class);
    private final Prestacion consulta = mock(Prestacion.class);
    private ServicioDeObrasSocialesBean servicio;

    @BeforeEach
    void armar() {
        servicio = Inyectar.en(new ServicioDeObrasSocialesBean(), "gateway", gateway);
    }

    @Test
    void unPacienteSinObraSocialEsParticularSinConsultarAlLegado() {
        Paciente particular = Fixtures.paciente(7L, null);

        ResultadoCobertura cobertura = servicio.validarCobertura(particular, consulta, LocalDateTime.now(), "turno-1");

        assertEquals(OrigenCobertura.SIN_OBRA_SOCIAL, cobertura.getOrigen());
        verify(gateway, never()).validarCobertura(any(), any(), any(), any());
    }

    @Test
    void conObraSocialSeConsultaAlLegado() {
        Paciente afiliado = Fixtures.paciente(7L, "OSDE");
        ResultadoCobertura delLegado = ResultadoCobertura.pendiente("sin respuesta");
        when(gateway.validarCobertura(afiliado, consulta, null, "turno-1")).thenReturn(delLegado);

        assertSame(delLegado, servicio.validarCobertura(afiliado, consulta, null, "turno-1"));
    }

    @Test
    void laAutorizacionViajaConLosDatosDelMensaje() {
        LocalDateTime fecha = LocalDateTime.of(2026, 12, 1, 10, 0);
        ResultadoAutorizacion autorizada = new ResultadoAutorizacion(true, "AUT-1", "Prestación autorizada.", "77");
        when(gateway.autorizar(any())).thenReturn(autorizada);

        ResultadoAutorizacion resultado = servicio.autorizar(new MensajeAutorizacion("sol-1", 10L, "OSDE", "210", "210-123",
                "420101", fecha, "MN 1"));

        assertSame(autorizada, resultado);
        verify(gateway).autorizar(argThat(p -> p.idSolicitud().equals("sol-1") && p.plan().equals("210")
                && p.nroAfiliado().equals("210-123") && p.fechaPrestacion().equals(fecha) && p.matriculaPrestador().equals("MN 1")));
    }

    /** Con el legado caído, la excepción hace que el broker reintente el mensaje (y, si sigue, lo deje en la DLQ). */
    @Test
    void conElLegadoCaidoLaAutorizacionFallaParaReintentarse() {
        when(gateway.autorizar(any())).thenThrow(new IntegracionNoDisponibleException("circuito abierto", false));

        assertThrows(ObraSocialNoDisponibleException.class,
                () -> servicio.autorizar(new MensajeAutorizacion("sol-1", 10L, "OSDE", "210", "1", "420101", null, "MN 1")));
    }

    @Test
    void elEstadoDeLaConexionEsElDelCircuito() {
        EstadoCircuito estado = new CircuitBreaker("Obra social (SOAP)", 3, Duration.ofSeconds(30)).instantanea();
        when(gateway.estadoCircuito()).thenReturn(estado);

        assertSame(estado, servicio.estadoConexion());
    }
}
