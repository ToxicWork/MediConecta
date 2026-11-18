package ar.edu.mediconecta.modelo;

import ar.edu.mediconecta.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-14: el profesional cierra la sala al terminar la videoconsulta; el turno sigue confirmado. */
class SalaDeVideoconsultaTest {

    private static final LocalDateTime HORA = LocalDateTime.of(2026, 11, 20, 10, 0);

    private static Turno teleconsultaConfirmada() {
        Turno turno = new Turno(Fixtures.profesional(20, "Clínica"), HORA, Modalidad.TELEMEDICINA);
        turno.reservar(Fixtures.paciente(7, "OSDE"), HORA.minusHours(1));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE 210", "210", "420101",
                new BigDecimal("25600"), new BigDecimal("7680"), new BigDecimal("17920"), EstadoAutorizacion.PENDIENTE));
        return turno;
    }

    @Test
    void sinSalaNoHayNadaQueCerrar() {
        Turno turno = teleconsultaConfirmada();
        assertFalse(turno.isSalaAbierta());
        assertEquals("La videoconsulta de este turno no se inició.",
                assertThrows(TransicionInvalidaException.class, () -> turno.cerrarSala(HORA)).getMessage());
    }

    @Test
    void alCerrarLaSalaDejaDeEstarAbiertaYQuedaFechada() {
        Turno turno = teleconsultaConfirmada();
        turno.asignarSala("sala-1", "https://video/p", "https://video/q");
        assertTrue(turno.isSalaAbierta());
        assertNull(turno.getSalaCerradaEn());

        turno.cerrarSala(HORA.plusMinutes(25));

        assertFalse(turno.isSalaAbierta());
        assertEquals(HORA.plusMinutes(25), turno.getSalaCerradaEn());
        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado(), "cerrar la sala no cambia el estado del turno");
    }

    @Test
    void cerrarDosVecesConservaLaPrimeraHora() {
        Turno turno = teleconsultaConfirmada();
        turno.asignarSala("sala-1", "https://video/p", "https://video/q");
        turno.cerrarSala(HORA.plusMinutes(25));
        turno.cerrarSala(HORA.plusMinutes(40));
        assertEquals(HORA.plusMinutes(25), turno.getSalaCerradaEn());
    }
}
