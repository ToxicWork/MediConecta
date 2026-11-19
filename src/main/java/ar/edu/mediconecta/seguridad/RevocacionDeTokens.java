package ar.edu.mediconecta.seguridad;

import ar.edu.mediconecta.dao.TokenRevocadoDAO;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.ConcurrencyManagement;
import jakarta.ejb.ConcurrencyManagementType;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.inject.Inject;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tokens revocados al cerrar sesión (ADR-0019). Cada pedido a la API consulta esta lista en memoria (sin ir a la
 * base); la base la respalda para que un reinicio del servidor no "resucite" un token revocado: al arrancar se
 * cargan los que todavía no vencieron. Una vez vencido, el token se rechaza solo y la fila se borra.
 */
@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
public class RevocacionDeTokens {

    private static final Logger LOG = Logger.getLogger(RevocacionDeTokens.class.getName());

    @Inject
    private TokenRevocadoDAO tokensRevocados;

    /** jti → cuándo vence el token (segundos Unix). */
    private final Map<String, Long> revocados = new ConcurrentHashMap<>();
    private Clock reloj = Clock.systemUTC();

    @PostConstruct
    void cargar() {
        try {
            tokensRevocados.vigentes(ahora()).forEach(t -> revocados.put(t.getJti(), t.getVenceEn()));
            LOG.info("Tokens revocados vigentes cargados: " + revocados.size() + ".");
        } catch (RuntimeException sinBase) {
            // Sin base al arrancar la aplicación igual despliega; las revocaciones de antes del reinicio no se ven
            // hasta el próximo arranque (a lo sumo, los 30 minutos de vigencia de un token).
            LOG.log(Level.WARNING, "No se pudieron cargar los tokens revocados; se sigue sin ellos.", sinBase);
        }
    }

    /** Revoca el token desde ya y hasta que venza. Un token sin jti (de una versión anterior) no se puede revocar. */
    public void revocar(IdentidadJwt identidad) {
        if (identidad.jti() == null) {
            return;
        }
        tokensRevocados.guardar(new TokenRevocado(identidad.jti(), identidad.venceEn()));
        revocados.put(identidad.jti(), identidad.venceEn());
    }

    public boolean estaRevocado(String jti) {
        if (jti == null) {
            return false;
        }
        Long vence = revocados.get(jti);
        if (vence != null && vence <= ahora()) {
            revocados.remove(jti); // ya venció: se rechaza solo por vencido
            return false;
        }
        return vence != null;
    }

    /** Cada hora se olvidan los que ya vencieron, en memoria y en la base. */
    @Schedule(minute = "53", hour = "*", persistent = false)
    void olvidarVencidos() {
        long ahora = ahora();
        revocados.values().removeIf(vence -> vence <= ahora);
        tokensRevocados.borrarVencidos(ahora);
    }

    private long ahora() {
        return reloj.instant().getEpochSecond();
    }
}
