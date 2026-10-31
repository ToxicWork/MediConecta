package ar.edu.mediconecta.integracion.resiliencia;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Circuit Breaker (Nygard, "Release It!"), implementado a mano porque el perfil standalone-full de
 * WildFly (el que trae el broker) no incluye MicroProfile Fault Tolerance.
 *
 * <ul>
 *   <li>CERRADO: las llamadas pasan. Tras {@code umbralFallas} fallas técnicas seguidas, se abre.</li>
 *   <li>ABIERTO: durante {@code espera} rechaza al instante sin llamar al sistema externo, así una
 *       caída del tercero no deja hilos colgados esperando timeouts.</li>
 *   <li>SEMIABIERTO: pasada la espera deja pasar una sola llamada de prueba: si anda se cierra, si
 *       falla vuelve a abrirse.</li>
 * </ul>
 * El lock sólo protege el cambio de estado: la llamada remota se hace fuera del {@code synchronized}.
 */
public class CircuitBreaker {

    public enum Estado { CERRADO, ABIERTO, SEMIABIERTO }

    private final String nombre;
    private final int umbralFallas;
    private final Duration espera;
    private final Clock reloj;

    private Estado estado = Estado.CERRADO;
    private int fallasConsecutivas;
    private Instant abiertoHasta;
    private boolean pruebaEnCurso;
    private long llamadasRechazadas;
    private Instant ultimoCambio;

    public CircuitBreaker(String nombre, int umbralFallas, Duration espera) {
        this(nombre, umbralFallas, espera, Clock.systemUTC());
    }

    public CircuitBreaker(String nombre, int umbralFallas, Duration espera, Clock reloj) {
        this.nombre = nombre;
        this.umbralFallas = umbralFallas;
        this.espera = espera;
        this.reloj = reloj;
        this.ultimoCambio = reloj.instant();
    }

    public <T> T ejecutar(Supplier<T> llamada) {
        autorizarLlamada();
        try {
            T resultado = llamada.get();
            registrarExito();
            return resultado;
        } catch (IntegracionNoDisponibleException falla) {
            registrarFalla();
            throw falla;
        } catch (RuntimeException otra) {
            // Un error que no es de disponibilidad (p. ej. de mapeo) no abre el circuito, pero libera la prueba.
            liberarPrueba();
            throw otra;
        }
    }

    private synchronized void autorizarLlamada() {
        if (estado == Estado.ABIERTO) {
            if (reloj.instant().isBefore(abiertoHasta)) {
                llamadasRechazadas++;
                throw new CircuitoAbiertoException(nombre, abiertoHasta);
            }
            cambiarA(Estado.SEMIABIERTO);
        }
        if (estado == Estado.SEMIABIERTO) {
            if (pruebaEnCurso) {
                llamadasRechazadas++;
                throw new CircuitoAbiertoException(nombre, null);
            }
            pruebaEnCurso = true;
        }
    }

    private synchronized void registrarExito() {
        fallasConsecutivas = 0;
        pruebaEnCurso = false;
        if (estado != Estado.CERRADO) {
            cambiarA(Estado.CERRADO);
        }
    }

    private synchronized void registrarFalla() {
        pruebaEnCurso = false;
        fallasConsecutivas++;
        if (estado == Estado.SEMIABIERTO || fallasConsecutivas >= umbralFallas) {
            abiertoHasta = reloj.instant().plus(espera);
            cambiarA(Estado.ABIERTO);
        }
    }

    private synchronized void liberarPrueba() {
        pruebaEnCurso = false;
    }

    private void cambiarA(Estado nuevo) {
        estado = nuevo;
        ultimoCambio = reloj.instant();
    }

    /** Foto del estado para el panel de integraciones. Un circuito abierto con la espera cumplida se informa semiabierto. */
    public synchronized EstadoCircuito instantanea() {
        Estado visible = estado == Estado.ABIERTO && !reloj.instant().isBefore(abiertoHasta) ? Estado.SEMIABIERTO : estado;
        return new EstadoCircuito(nombre, visible, fallasConsecutivas, umbralFallas, espera.toSeconds(),
                visible == Estado.ABIERTO ? abiertoHasta : null, llamadasRechazadas, ultimoCambio);
    }

    public synchronized Estado getEstado() {
        return instantanea().getEstado();
    }
}
