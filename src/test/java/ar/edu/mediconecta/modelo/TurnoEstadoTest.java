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

    /** La matriz completa: las doce combinaciones de estado y operación que el State no admite. */
    @Test
    void cadaEstadoRechazaTodasLasOperacionesQueNoAdmite() {
        Paciente otro = Fixtures.paciente(8, null);

        Turno disponible = Fixtures.turno(MANANA);
        assertInvalida(() -> disponible.confirmar(confirmacion()), "No se puede confirmar un turno disponible.");
        assertInvalida(disponible::liberar, "No se puede liberar un turno disponible.");
        assertInvalida(disponible::cancelar, "No se puede cancelar un turno disponible.");

        Turno enHold = Fixtures.turno(MANANA);
        enHold.reservar(Fixtures.paciente(7, null), MANANA);
        assertInvalida(() -> enHold.reservar(otro, MANANA), "No se puede reservar un turno en espera.");
        assertInvalida(enHold::cancelar, "No se puede cancelar un turno en espera.");

        Turno confirmado = Fixtures.turno(MANANA);
        confirmado.reservar(Fixtures.paciente(7, null), MANANA);
        confirmado.confirmar(confirmacion());
        assertInvalida(() -> confirmado.reservar(otro, MANANA), "No se puede reservar un turno confirmado.");
        assertInvalida(() -> confirmado.confirmar(confirmacion()), "No se puede confirmar un turno confirmado.");
        assertInvalida(confirmado::liberar, "No se puede liberar un turno confirmado.");

        confirmado.cancelar();
        assertInvalida(() -> confirmado.reservar(otro, MANANA), "No se puede reservar un turno cancelado.");
        assertInvalida(() -> confirmado.confirmar(confirmacion()), "No se puede confirmar un turno cancelado.");
        assertInvalida(confirmado::liberar, "No se puede liberar un turno cancelado.");
        assertInvalida(confirmado::cancelar, "No se puede cancelar un turno cancelado.");
        assertEquals(EstadoTurno.CANCELADO, confirmado.getEstado(), "una transición inválida no cambia el estado");
    }

    private static void assertInvalida(org.junit.jupiter.api.function.Executable operacion, String mensaje) {
        assertEquals(mensaje, assertThrows(TransicionInvalidaException.class, operacion).getMessage());
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
