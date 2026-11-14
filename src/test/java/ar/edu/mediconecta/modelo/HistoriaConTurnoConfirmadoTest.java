package ar.edu.mediconecta.modelo;

import ar.edu.mediconecta.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEC-D01: una entrada de historia clínica sólo se asocia a un turno confirmado entre ese profesional y ese
 * paciente. (Que el profesional pueda abrir la historia lo decide ServicioDeHistoriaClinicaBean con la misma
 * condición sobre la base; lo prueba el escenario D01 de pruebas/prueba_seguridad.py.)
 */
class HistoriaConTurnoConfirmadoTest {

    private static final long PROFESIONAL = 20, PACIENTE = 7, OTRO = 99;

    private static Turno turnoEnHold() {
        Turno turno = Fixtures.turno(LocalDateTime.now().plusDays(2));
        turno.reservar(Fixtures.paciente(PACIENTE, "OSDE"), LocalDateTime.now().plusMinutes(5));
        return turno;
    }

    private static Turno turnoConfirmado() {
        Turno turno = turnoEnHold();
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE 210", "210", "420101",
                new BigDecimal("32000"), new BigDecimal("9600"), new BigDecimal("22400"), EstadoAutorizacion.PENDIENTE));
        return turno;
    }

    @Test
    void conTurnoConfirmadoEntreAmbosSePuede() {
        assertTrue(turnoConfirmado().habilitaHistoriaClinica(PROFESIONAL, PACIENTE));
    }

    @Test
    void unTurnoDeOtroProfesionalODeOtroPacienteNo() {
        Turno turno = turnoConfirmado();
        assertFalse(turno.habilitaHistoriaClinica(OTRO, PACIENTE), "turno de otro profesional");
        assertFalse(turno.habilitaHistoriaClinica(PROFESIONAL, OTRO), "turno de otro paciente");
    }

    @Test
    void unTurnoSinConfirmarOCanceladoNo() {
        assertFalse(Fixtures.turno(LocalDateTime.now().plusDays(2)).habilitaHistoriaClinica(PROFESIONAL, PACIENTE),
                "turno disponible, sin paciente");
        assertFalse(turnoEnHold().habilitaHistoriaClinica(PROFESIONAL, PACIENTE), "turno en espera (hold)");
        Turno cancelado = turnoConfirmado();
        cancelado.cancelar();
        assertFalse(cancelado.habilitaHistoriaClinica(PROFESIONAL, PACIENTE), "turno cancelado");
    }
}
