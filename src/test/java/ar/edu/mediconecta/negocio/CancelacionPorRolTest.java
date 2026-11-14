package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.modelo.Administrativo;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.TransicionInvalidaException;
import ar.edu.mediconecta.modelo.Turno;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cancelación por el paciente, el profesional y el administrativo: quién puede cancelar cada turno (titularidad)
 * y cuándo el horario se vuelve a ofrecer (sólo si canceló el paciente y el turno no pasó).
 */
class CancelacionPorRolTest {

    private static final LocalDateTime AHORA = LocalDateTime.now();
    private static final long PROFESIONAL = 20, PACIENTE = 7;

    private static Turno confirmado(LocalDateTime fechaHora) {
        Turno turno = Fixtures.turno(fechaHora);
        turno.reservar(Fixtures.paciente(PACIENTE, "OSDE"), AHORA.plusMinutes(5));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE 210", "210", "420101",
                new BigDecimal("32000"), new BigDecimal("9600"), new BigDecimal("22400"), EstadoAutorizacion.PENDIENTE));
        return turno;
    }

    @Test
    void elPacienteCancelaSoloSusTurnos() {
        Turno turno = confirmado(AHORA.plusDays(3));
        assertTrue(ProcesoCancelacionTurno.puedeCancelar(Fixtures.paciente(PACIENTE, "OSDE"), turno));
        assertFalse(ProcesoCancelacionTurno.puedeCancelar(Fixtures.paciente(99, "OSDE"), turno), "turno de otro paciente");
    }

    @Test
    void elProfesionalCancelaSoloLosDeSuAgenda() {
        Turno turno = confirmado(AHORA.plusDays(3));
        assertTrue(ProcesoCancelacionTurno.puedeCancelar(Fixtures.profesional(PROFESIONAL, "Cardiología"), turno));
        assertFalse(ProcesoCancelacionTurno.puedeCancelar(Fixtures.profesional(21, "Clínica"), turno), "turno de otro profesional");
    }

    @Test
    void elAdministrativoCancelaCualquiera() {
        Turno turno = confirmado(AHORA.plusDays(3));
        assertTrue(ProcesoCancelacionTurno.puedeCancelar(
                Fixtures.conId(new Administrativo("Carla Admin", "carla@test", "x"), 30), turno));
    }

    @Test
    void quedaRegistradoQuienCancelo() {
        Turno turno = confirmado(AHORA.plusDays(3));
        turno.cancelar(Rol.PROFESIONAL);
        assertEquals(EstadoTurno.CANCELADO, turno.getEstado());
        assertEquals(Rol.PROFESIONAL, turno.getCanceladoPor());
    }

    @Test
    void elHorarioSeVuelveAOfrecerSoloSiCanceloElPacienteYNoPaso() {
        Turno porPaciente = confirmado(AHORA.plusDays(3));
        porPaciente.cancelar(Rol.PACIENTE);
        assertTrue(porPaciente.liberaElHorario(AHORA));

        Turno porProfesional = confirmado(AHORA.plusDays(3));
        porProfesional.cancelar(Rol.PROFESIONAL);
        assertFalse(porProfesional.liberaElHorario(AHORA), "si cancela el profesional no se vuelve a ofrecer");

        Turno porAdministrativo = confirmado(AHORA.plusDays(3));
        porAdministrativo.cancelar(Rol.ADMINISTRATIVO);
        assertFalse(porAdministrativo.liberaElHorario(AHORA), "si cancela la administración no se vuelve a ofrecer");

        Turno pasado = confirmado(AHORA.plusDays(3));
        pasado.cancelar(Rol.PACIENTE);
        assertFalse(pasado.liberaElHorario(AHORA.plusDays(4)), "un horario que ya pasó no se ofrece");
    }

    @Test
    void cualquieraSeaElRolElPatronStateRechazaCancelarDosVeces() {
        Turno turno = confirmado(AHORA.plusDays(3));
        turno.cancelar(Rol.ADMINISTRATIVO);
        assertThrows(TransicionInvalidaException.class, () -> turno.cancelar(Rol.PROFESIONAL));
        assertEquals(Rol.ADMINISTRATIVO, turno.getCanceladoPor(), "un intento fallido no cambia quién canceló");
    }
}
