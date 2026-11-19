package ar.edu.mediconecta;

import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Turno;
import org.junit.jupiter.params.provider.Arguments;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Las doce combinaciones de estado y operación que el patrón State del turno no admite, con el mensaje de cada una.
 * Las usan la prueba del dominio ({@code TurnoEstadoTest}: lanza la excepción y no cambia el estado) y la de la API
 * ({@code MapeadorDeExcepcionesTest}: cada una se responde con 409).
 */
public final class TransicionesInvalidas {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 17, 10, 0);
    private static final LocalDateTime MANANA = AHORA.plusDays(1);

    private TransicionesInvalidas() {
    }

    /** Argumentos de cada caso: estado de partida, operación, cómo aplicarla y el mensaje esperado. */
    public static Stream<Arguments> casos() {
        Consumer<Turno> reservar = turno -> turno.reservar(Fixtures.paciente(8, null), MANANA);
        Consumer<Turno> confirmar = turno -> turno.confirmar(confirmacion());
        Consumer<Turno> liberar = Turno::liberar;
        Consumer<Turno> cancelar = turno -> turno.cancelar(Rol.PACIENTE, AHORA);
        return Stream.of(
                arguments(EstadoTurno.DISPONIBLE, "confirmar", confirmar, "No se puede confirmar un turno disponible."),
                arguments(EstadoTurno.DISPONIBLE, "liberar", liberar, "No se puede liberar un turno disponible."),
                arguments(EstadoTurno.DISPONIBLE, "cancelar", cancelar, "No se puede cancelar un turno disponible."),
                arguments(EstadoTurno.HOLD, "reservar", reservar, "No se puede reservar un turno en espera."),
                arguments(EstadoTurno.HOLD, "cancelar", cancelar, "No se puede cancelar un turno en espera."),
                arguments(EstadoTurno.CONFIRMADO, "reservar", reservar, "No se puede reservar un turno confirmado."),
                arguments(EstadoTurno.CONFIRMADO, "confirmar", confirmar, "No se puede confirmar un turno confirmado."),
                arguments(EstadoTurno.CONFIRMADO, "liberar", liberar, "No se puede liberar un turno confirmado."),
                arguments(EstadoTurno.CANCELADO, "reservar", reservar, "No se puede reservar un turno cancelado."),
                arguments(EstadoTurno.CANCELADO, "confirmar", confirmar, "No se puede confirmar un turno cancelado."),
                arguments(EstadoTurno.CANCELADO, "liberar", liberar, "No se puede liberar un turno cancelado."),
                arguments(EstadoTurno.CANCELADO, "cancelar", cancelar, "No se puede cancelar un turno cancelado."));
    }

    /** Un turno de mañana llevado al estado pedido por el camino válido (reservar, confirmar, cancelar). */
    public static Turno enEstado(EstadoTurno estado) {
        Turno turno = Fixtures.turno(MANANA);
        if (estado == EstadoTurno.DISPONIBLE) {
            return turno;
        }
        turno.reservar(Fixtures.paciente(7, null), MANANA);
        if (estado == EstadoTurno.HOLD) {
            return turno;
        }
        turno.confirmar(confirmacion());
        if (estado == EstadoTurno.CONFIRMADO) {
            return turno;
        }
        turno.cancelar(Rol.PACIENTE, AHORA);
        return turno;
    }

    private static DatosConfirmacion confirmacion() {
        return new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE 210", "210", "420101",
                new BigDecimal("32000"), new BigDecimal("9600"), new BigDecimal("22400"), EstadoAutorizacion.PENDIENTE);
    }
}
