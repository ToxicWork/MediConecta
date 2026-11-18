package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.NotificacionDAO;
import ar.edu.mediconecta.dao.PedidoIdempotenteDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Las tareas programadas de mantenimiento y las notificaciones de la campanita. */
class TareasProgramadasYNotificacionesTest {

    @Test
    void cadaMinutoSeEliminanLosHorariosQueVencieronSinReservarse() {
        TurnoDAO turnoDAO = mock(TurnoDAO.class);
        VencedorDeDisponibilidad vencedor = Inyectar.en(new VencedorDeDisponibilidad(), "turnoDAO", turnoDAO);
        LocalDateTime antes = LocalDateTime.now();

        Inyectar.invocar(vencedor, "eliminarVencidos");

        verify(turnoDAO).eliminarDisponiblesVencidos(argThat(ahora -> !ahora.isBefore(antes)));
    }

    /** Las respuestas guardadas por Idempotency-Key valen 24 horas: después, la misma clave es un pedido nuevo. */
    @Test
    void cadaHoraSeBorranLasRespuestasIdempotentesDeMasDe24Horas() {
        PedidoIdempotenteDAO pedidos = mock(PedidoIdempotenteDAO.class);
        when(pedidos.borrarAnterioresA(any())).thenReturn(3);
        LimpiezaDePedidosIdempotentes limpieza = Inyectar.en(new LimpiezaDePedidosIdempotentes(), "pedidos", pedidos);
        LocalDateTime haceUnDia = LocalDateTime.now().minus(Duration.ofHours(24));

        Inyectar.invocar(limpieza, "borrarVencidos");

        verify(pedidos).borrarAnterioresA(argThat(limite ->
                Duration.between(haceUnDia.atZone(ZoneId.systemDefault()), limite.atZone(ZoneId.systemDefault()))
                        .abs().compareTo(Duration.ofSeconds(5)) < 0));
    }

    @Test
    void lasNotificacionesSeMarcanLeidasDeAUnaOTodasJuntas() {
        NotificacionDAO dao = mock(NotificacionDAO.class);
        ServicioDeNotificacionesBean notificaciones = Inyectar.en(new ServicioDeNotificacionesBean(), "notificacionDAO", dao);
        when(dao.marcarLeida(7L, 3L)).thenReturn(true);
        when(dao.contarNoLeidas(7L)).thenReturn(2L);

        assertTrue(notificaciones.marcarLeida(7L, 3L));
        assertFalse(notificaciones.marcarLeida(7L, null), "sin id no hay nada que marcar");
        notificaciones.marcarTodasLeidas(7L);
        notificaciones.listarDeUsuario(7L);

        verify(dao).marcarLeidas(7L);
        verify(dao).buscarDeUsuario(7L);
        verify(dao, never()).marcarLeida(anyLong(), argThat(id -> id == null));
        assertTrue(notificaciones.contarNoLeidas(7L) == 2L);
    }
}
