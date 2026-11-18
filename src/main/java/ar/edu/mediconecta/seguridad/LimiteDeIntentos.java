package ar.edu.mediconecta.seguridad;

import ar.edu.mediconecta.modelo.PoliticaDeBloqueo;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Límite de intentos de ingreso fallidos por email, en memoria: el que responde 429 ante la fuerza bruta (Eje D).
 * Usa los mismos números que el bloqueo de la cuenta ({@link PoliticaDeBloqueo}): al quinto fallo seguido, 15 minutos
 * sin poder intentar; un ingreso correcto pone el contador en cero.
 *
 * <p>Cuenta igual un email que existe y uno que no: el 429 dice que se intentó demasiado con ese email, nunca si la
 * cuenta existe. El bloqueo de la cuenta en la base sigue siendo la protección de fondo: si este registro se llena
 * (un ataque con miles de emails distintos) o se reinicia el servidor, la API vuelve a responder 401 a todos por igual.
 *
 * <p>Los métodos con {@code ahora} son puros, para probarlos sin contenedor.
 */
@ApplicationScoped
public class LimiteDeIntentos {

    static final int MAXIMO_DE_EMAILS = 10_000;

    private final int maximo;
    private final Duration bloqueo;
    private final Map<String, Registro> porEmail = new ConcurrentHashMap<>();

    /** Fallos seguidos, cuándo fue el último y, si llegó al máximo, hasta cuándo no se puede intentar. */
    private record Registro(int fallos, Instant ultimoFallo, Instant bloqueadoHasta) {

        boolean bloqueadoEn(Instant ahora) {
            return bloqueadoHasta != null && bloqueadoHasta.isAfter(ahora);
        }
    }

    public LimiteDeIntentos() {
        this(PoliticaDeBloqueo.MAX_INTENTOS_FALLIDOS, PoliticaDeBloqueo.DURACION_BLOQUEO);
    }

    LimiteDeIntentos(int maximo, Duration bloqueo) {
        this.maximo = maximo;
        this.bloqueo = bloqueo;
    }

    /** Cuánto falta para poder volver a intentar con este email; vacío si se puede ahora. */
    public Optional<Duration> espera(String email) {
        return espera(email, Instant.now());
    }

    public boolean registrarFallo(String email) {
        return registrarFallo(email, Instant.now());
    }

    /** Un ingreso correcto pone el contador en cero. */
    public void registrarIngreso(String email) {
        porEmail.remove(clave(email));
    }

    /** Lo usa el administrativo que desbloquea una cuenta: también puede volver a intentar por la API. */
    public void olvidar(String email) {
        porEmail.remove(clave(email));
    }

    Optional<Duration> espera(String email, Instant ahora) {
        Registro registro = porEmail.get(clave(email));
        return registro != null && registro.bloqueadoEn(ahora)
                ? Optional.of(Duration.between(ahora, registro.bloqueadoHasta()))
                : Optional.empty();
    }

    /** Suma un fallo; devuelve {@code true} si con éste el email queda bloqueado. */
    boolean registrarFallo(String email, Instant ahora) {
        String clave = clave(email);
        if (!porEmail.containsKey(clave) && !hayLugar(ahora)) {
            return false;
        }
        boolean[] seBloqueo = {false};
        porEmail.compute(clave, (k, anterior) -> {
            if (anterior != null && anterior.bloqueadoEn(ahora)) {
                return anterior;
            }
            int fallos = anterior == null || anterior.bloqueadoHasta() != null ? 1 : anterior.fallos() + 1;
            if (fallos >= maximo) {
                seBloqueo[0] = true;
                return new Registro(0, ahora, ahora.plus(bloqueo));
            }
            return new Registro(fallos, ahora, null);
        });
        return seBloqueo[0];
    }

    int emailsRegistrados() {
        return porEmail.size();
    }

    /** Si el registro está lleno, descarta lo que ya no bloquea ni es reciente; si sigue lleno, no anota emails nuevos. */
    private boolean hayLugar(Instant ahora) {
        if (porEmail.size() < MAXIMO_DE_EMAILS) {
            return true;
        }
        Instant viejo = ahora.minus(bloqueo);
        porEmail.values().removeIf(r -> !r.bloqueadoEn(ahora) && r.ultimoFallo().isBefore(viejo));
        return porEmail.size() < MAXIMO_DE_EMAILS;
    }

    static String clave(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
