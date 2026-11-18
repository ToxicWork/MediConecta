package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Turno;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El temporizador de respaldo: libera los holds vencidos, pero relee cada turno bloqueado antes (D-36) y no toca el que
 * en el medio se confirmó o se renovó.
 */
class LiberadorDeHoldsTest {

    @Test
    void liberaSoloLosHoldsQueSiguenVencidosAlReleerlos() {
        TurnoDAO turnoDAO = mock(TurnoDAO.class);
        LiberadorDeHolds liberador = Inyectar.en(new LiberadorDeHolds(), "turnoDAO", turnoDAO);
        Turno vencido = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(1)), 1L);
        vencido.reservar(Fixtures.paciente(7L, "OSDE"), LocalDateTime.now().minusMinutes(1));
        Turno renovado = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(1)), 2L);
        renovado.reservar(Fixtures.paciente(8L, "OSDE"), LocalDateTime.now().plusMinutes(4));
        Turno yaLiberado = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(1)), 3L);
        when(turnoDAO.buscarHoldsVencidos(any())).thenReturn(List.of(vencido, renovado, yaLiberado));

        Inyectar.invocar(liberador, "liberarVencidos");

        assertEquals(EstadoTurno.DISPONIBLE, vencido.getEstado());
        assertEquals(EstadoTurno.HOLD, renovado.getEstado());
        assertEquals(EstadoTurno.DISPONIBLE, yaLiberado.getEstado());
        verify(turnoDAO).refrescarParaActualizar(vencido);
        verify(turnoDAO).refrescarParaActualizar(renovado);
        verify(turnoDAO).refrescarParaActualizar(yaLiberado);
    }
}
