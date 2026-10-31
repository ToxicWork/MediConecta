package ar.edu.mediconecta.modelo;

import ar.edu.mediconecta.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Patrón State: transiciones válidas e inválidas del turno. */
class TurnoEstadoTest {

    private static final LocalDateTime MANANA = LocalDateTime.now().plusDays(1);

    private static DatosConfirmacion confirmacion() {
        return new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE 210", "210", "420101",
                new BigDecimal("32000"), new BigDecimal("9600"), new BigDecimal("22400"), EstadoAutorizacion.PENDIENTE);
    }

    @Test
    void recorridoFelizDisponibleHoldConfirmadoCancelado() {
        Turno turno = Fixtures.turno(MANANA);
        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());

        turno.reservar(Fixtures.paciente(7, "OSDE"), MANANA.minusHours(1));
        assertEquals(EstadoTurno.HOLD, turno.getEstado());
        assertTrue(turno.perteneceAPaciente(7L));

        turno.confirmar(confirmacion());
        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        assertEquals(new BigDecimal("9600"), turno.getImportePaciente());
        assertNull(turno.getHoldVenceEn());

        turno.cancelar();
        assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        assertTrue(turno.perteneceAPaciente(7L), "el cancelado conserva al paciente como historial");
    }

    @Test
    void liberarUnHoldDevuelveElTurnoADisponibleSinPaciente() {
        Turno turno = Fixtures.turno(MANANA);
        turno.reservar(Fixtures.paciente(7, null), MANANA);
        turno.liberar();
        assertEquals(EstadoTurno.DISPONIBLE, turno.getEstado());
        assertNull(turno.getPaciente());
    }

    @Test
    void transicionesInvalidasSonImposibles() {
        Turno disponible = Fixtures.turno(MANANA);
        assertThrows(TransicionInvalidaException.class, disponible::cancelar);
        assertThrows(TransicionInvalidaException.class, () -> disponible.confirmar(confirmacion()));

        Turno cancelado = Fixtures.turno(MANANA);
        cancelado.reservar(Fixtures.paciente(7, null), MANANA);
        cancelado.confirmar(confirmacion());
        cancelado.cancelar();
        assertThrows(TransicionInvalidaException.class, cancelado::cancelar);
        assertThrows(TransicionInvalidaException.class, cancelado::liberar);
        assertThrows(TransicionInvalidaException.class, () -> cancelado.reservar(Fixtures.paciente(8, null), MANANA));
    }

    @Test
    void laAutorizacionSoloSeRegistraEnUnTurnoConfirmadoYCompletaLaCoberturaPendiente() {
        Turno turno = Fixtures.turno(MANANA);
        assertThrows(TransicionInvalidaException.class, () -> turno.registrarAutorizacion(true, "AUT-1", null));

        turno.reservar(Fixtures.paciente(7, "OSDE"), MANANA);
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.PENDIENTE, "OSDE", null, "420101",
                new BigDecimal("32000"), BigDecimal.ZERO, BigDecimal.ZERO, EstadoAutorizacion.PENDIENTE));
        turno.registrarAutorizacion(true, "AUT-1", "ok");
        assertEquals(EstadoAutorizacion.AUTORIZADA, turno.getEstadoAutorizacion());
        assertEquals(EstadoCobertura.CUBIERTA, turno.getCobertura());
    }

    @Test
    void alCancelarElHorarioSeRepublicaComoTurnoNuevo() {
        Turno turno = Fixtures.turno(MANANA);
        Turno nuevo = turno.horarioLiberado();
        assertEquals(EstadoTurno.DISPONIBLE, nuevo.getEstado());
        assertEquals(turno.getFechaHora(), nuevo.getFechaHora());
        assertEquals(turno.getModalidad(), nuevo.getModalidad());
    }
}
