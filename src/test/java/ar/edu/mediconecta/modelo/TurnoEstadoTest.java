package ar.edu.mediconecta.modelo;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.TransicionesInvalidas;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Patrón State: transiciones válidas e inválidas del turno. */
class TurnoEstadoTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final LocalDateTime MANANA = AHORA.plusDays(1);

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

        turno.cancelar(Rol.PACIENTE, AHORA);
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

    /**
     * La matriz completa del State: cada una de las doce combinaciones de estado y operación que no admite es un caso
     * propio (la tabla está en {@link TransicionesInvalidas}). Lanza TransicionInvalidaException con su mensaje, que la
     * API responde con 409 (MapeadorDeExcepcionesTest), y no cambia el estado.
     */
    @ParameterizedTest(name = "{0} + {1}: «{3}»")
    @MethodSource("ar.edu.mediconecta.TransicionesInvalidas#casos")
    void cadaEstadoRechazaCadaOperacionQueNoAdmite(EstadoTurno estado, String operacion, Consumer<Turno> aplicar,
                                                   String mensaje) {
        Turno turno = TransicionesInvalidas.enEstado(estado);
        assertEquals(estado, turno.getEstado());

        assertInvalida(() -> aplicar.accept(turno), mensaje);

        assertEquals(estado, turno.getEstado(), "una transición inválida no cambia el estado");
    }

    private static void assertInvalida(org.junit.jupiter.api.function.Executable operacion, String mensaje) {
        assertEquals(mensaje, assertThrows(TransicionInvalidaException.class, operacion).getMessage());
    }

    @Test
    void transicionesInvalidasSonImposibles() {
        Turno disponible = Fixtures.turno(MANANA);
        assertThrows(TransicionInvalidaException.class, () -> disponible.cancelar(Rol.PACIENTE, AHORA));
        assertThrows(TransicionInvalidaException.class, () -> disponible.confirmar(confirmacion()));

        Turno cancelado = Fixtures.turno(MANANA);
        cancelado.reservar(Fixtures.paciente(7, null), MANANA);
        cancelado.confirmar(confirmacion());
        cancelado.cancelar(Rol.PACIENTE, AHORA);
        assertThrows(TransicionInvalidaException.class, () -> cancelado.cancelar(Rol.PACIENTE, AHORA));
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

    /** D-13: un turno confirmado se cancela sólo antes de su hora; desde la hora de inicio en adelante, nunca. */
    @Test
    void unTurnoConfirmadoSoloSeCancelaAntesDeSuHora() {
        Turno turno = confirmadoPara(MANANA);
        assertTrue(turno.admiteCancelacion(MANANA.minusMinutes(1)), "un minuto antes todavía se puede");
        assertFalse(turno.admiteCancelacion(MANANA), "a la hora exacta ya empezó");
        assertFalse(turno.admiteCancelacion(MANANA.plusDays(15)), "un turno de hace dos semanas tampoco");

        assertInvalida(() -> turno.cancelar(Rol.PACIENTE, MANANA), "El turno ya empezó: no se puede cancelar.");
        assertInvalida(() -> turno.cancelar(Rol.PROFESIONAL, MANANA.plusMinutes(10)), "El turno ya empezó: no se puede cancelar.");
        assertInvalida(() -> turno.cancelar(Rol.ADMINISTRATIVO, MANANA.plusDays(15)), "El turno ya empezó: no se puede cancelar.");
        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado(), "el intento rechazado no cambia el estado");
        assertNull(turno.getCanceladoPor(), "ni registra quién canceló");
        assertNull(turno.getCanceladoEn());

        turno.cancelar(Rol.PACIENTE, MANANA.minusMinutes(1));
        assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        assertEquals(MANANA.minusMinutes(1), turno.getCanceladoEn(), "la cancelación queda fechada con el reloj recibido");
    }

    /** Sólo un turno confirmado admite cancelación, aunque falte mucho para su hora. */
    @Test
    void ningunOtroEstadoAdmiteCancelacion() {
        Turno disponible = Fixtures.turno(MANANA);
        Turno enHold = Fixtures.turno(MANANA);
        enHold.reservar(Fixtures.paciente(7, null), MANANA);
        Turno cancelado = confirmadoPara(MANANA);
        cancelado.cancelar(Rol.PACIENTE, AHORA);
        assertFalse(disponible.admiteCancelacion(AHORA));
        assertFalse(enHold.admiteCancelacion(AHORA));
        assertFalse(cancelado.admiteCancelacion(AHORA));
    }

    private static Turno confirmadoPara(LocalDateTime fechaHora) {
        Turno turno = Fixtures.turno(fechaHora);
        turno.reservar(Fixtures.paciente(7, "OSDE"), fechaHora.minusHours(1));
        turno.confirmar(confirmacion());
        return turno;
    }
}
