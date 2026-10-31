package ar.edu.mediconecta.negocio.validacion;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Chain of Responsibility: la solicitud recorre los eslabones y el primero que falla corta la cadena. */
class ValidadorReservaTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 1, 10, 0);

    private static ValidadorReserva cadenaDeConfirmacion(SinSuperposicion.ConsultaSuperposicion consulta) {
        ValidadorReserva cadena = new TurnoExistente();
        cadena.enlazar(new HoldDelPaciente())
                .enlazar(new HoldVigente())
                .enlazar(new HorarioFuturo())
                .enlazar(new SinSuperposicion(consulta));
        return cadena;
    }

    private static Turno turnoEnHoldDe(long pacienteId, LocalDateTime venceEn) {
        Turno turno = Fixtures.turno(AHORA.plusDays(2));
        turno.reservar(Fixtures.paciente(pacienteId, "OSDE"), venceEn);
        return turno;
    }

    @Test
    void unaReservaValidaRecorreTodaLaCadena() {
        AtomicInteger consultas = new AtomicInteger();
        ValidadorReserva cadena = cadenaDeConfirmacion((p, f, e) -> {
            consultas.incrementAndGet();
            return false;
        });
        assertDoesNotThrow(() -> cadena.validar(new SolicitudReserva(turnoEnHoldDe(7, AHORA.plusMinutes(5)), 7L, AHORA)));
        assertEquals(1, consultas.get(), "el último eslabón se ejecutó");
    }

    @Test
    void elPrimerEslabonQueFallaCortaLaCadena() {
        AtomicInteger consultas = new AtomicInteger();
        ValidadorReserva cadena = cadenaDeConfirmacion((p, f, e) -> {
            consultas.incrementAndGet();
            return false;
        });
        ReservaInvalidaException ajeno = assertThrows(ReservaInvalidaException.class,
                () -> cadena.validar(new SolicitudReserva(turnoEnHoldDe(7, AHORA.plusMinutes(5)), 99L, AHORA)));
        assertEquals("El turno ya no está retenido a tu nombre.", ajeno.getMessage());
        assertEquals(0, consultas.get(), "los eslabones siguientes no se ejecutan");
    }

    @Test
    void reglasIndividuales() {
        ValidadorReserva cadena = cadenaDeConfirmacion((p, f, e) -> false);
        assertThrows(ReservaInvalidaException.class, () -> cadena.validar(new SolicitudReserva(null, 7L, AHORA)));
        assertThrows(ReservaInvalidaException.class,
                () -> cadena.validar(new SolicitudReserva(turnoEnHoldDe(7, AHORA.minusMinutes(1)), 7L, AHORA)),
                "hold vencido");
        ValidadorReserva conSuperposicion = cadenaDeConfirmacion((p, f, e) -> true);
        assertThrows(ReservaInvalidaException.class,
                () -> conSuperposicion.validar(new SolicitudReserva(turnoEnHoldDe(7, AHORA.plusMinutes(5)), 7L, AHORA)),
                "otro turno confirmado en el mismo horario");
    }
}
