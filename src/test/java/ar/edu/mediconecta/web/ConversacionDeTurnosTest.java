package ar.edu.mediconecta.web;

import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.ServicioDeTurnos;
import ar.edu.mediconecta.negocio.excepciones.TurnoNoDisponibleException;
import jakarta.ejb.NoSuchEJBException;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-43: la conversación de reserva que venció a los 5 minutos se renueva sola, sin error 500 en la Agenda. */
class ConversacionDeTurnosTest {

    private static final Long PROFESIONAL = 7L;

    private final ServicioDeTurnos vencida = mock(ServicioDeTurnos.class);
    private final ServicioDeTurnos nueva = mock(ServicioDeTurnos.class);
    @SuppressWarnings("unchecked")
    private final Instance<ServicioDeTurnos> conversaciones = mock(Instance.class);
    private final ConversacionDeTurnos conversacion = new ConversacionDeTurnos();

    @BeforeEach
    void preparar() {
        when(conversaciones.get()).thenReturn(vencida, nueva);
        Inyectar.en(conversacion, "conversaciones", conversaciones);
    }

    @Test
    void mientrasEstaVigenteUsaSiempreLaMismaConversacion() {
        List<Turno> libres = List.of(mock(Turno.class));
        when(vencida.buscarDisponibilidad(PROFESIONAL)).thenReturn(libres);

        assertSame(libres, conversacion.llamar(s -> s.buscarDisponibilidad(PROFESIONAL)));
        assertSame(libres, conversacion.llamar(s -> s.buscarDisponibilidad(PROFESIONAL)));

        verify(conversaciones, times(1)).get();
        verify(conversaciones, never()).destroy(vencida);
    }

    @Test
    void siVencioEmpiezaUnaNuevaYRepiteElPedido() {
        List<Turno> libres = List.of(mock(Turno.class));
        when(vencida.buscarDisponibilidad(PROFESIONAL)).thenThrow(new NoSuchEJBException("WFLYEJB0168"));
        when(nueva.buscarDisponibilidad(PROFESIONAL)).thenReturn(libres);

        assertSame(libres, conversacion.llamar(s -> s.buscarDisponibilidad(PROFESIONAL)));
        assertEquals(List.of(), conversacion.llamar(s -> s.buscarProximosConfirmados(PROFESIONAL)),
                "los pedidos siguientes ya van a la conversación nueva");

        verify(conversaciones, times(2)).get();
        verify(conversaciones).destroy(vencida);
        verify(nueva).buscarProximosConfirmados(PROFESIONAL);
    }

    @Test
    void siElContenedorYaLaHabiaDestruidoIgualSigue() {
        when(vencida.buscarDisponibilidad(PROFESIONAL)).thenThrow(new NoSuchEJBException("WFLYEJB0168"));
        doThrow(new NoSuchEJBException("ya no existe")).when(conversaciones).destroy(vencida);
        when(nueva.buscarDisponibilidad(PROFESIONAL)).thenReturn(List.of());

        assertEquals(List.of(), conversacion.llamar(s -> s.buscarDisponibilidad(PROFESIONAL)));
    }

    @Test
    void lasAccionesSinResultadoTambienSeRenuevan() {
        doThrow(new NoSuchEJBException("WFLYEJB0168")).when(vencida).liberarHold();

        conversacion.ejecutar(ServicioDeTurnos::liberarHold);

        verify(nueva).liberarHold();
    }

    @Test
    void siLaNuevaTambienFallaNoInsiste() {
        when(vencida.buscarDisponibilidad(PROFESIONAL)).thenThrow(new NoSuchEJBException("WFLYEJB0168"));
        when(nueva.buscarDisponibilidad(PROFESIONAL)).thenThrow(new NoSuchEJBException("otra vez"));

        assertThrows(NoSuchEJBException.class, () -> conversacion.llamar(s -> s.buscarDisponibilidad(PROFESIONAL)));
        verify(conversaciones, times(2)).get();
    }

    @Test
    void unErrorDeNegocioNoRenuevaLaConversacion() {
        TurnoNoDisponibleException sinHold = new TurnoNoDisponibleException("No hay ningún turno en espera para confirmar.");
        when(vencida.confirmar(null)).thenThrow(sinHold);

        assertSame(sinHold, assertThrows(TurnoNoDisponibleException.class, () -> conversacion.llamar(s -> s.confirmar(null))));
        verify(conversaciones, times(1)).get();
    }
}
