package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.PedidoIdempotenteDAO;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Una vez por hora borra las respuestas guardadas por {@code Idempotency-Key} que tienen más de 24 horas: pasado ese
 * plazo, un pedido con la misma clave se trata como nuevo.
 */
@Singleton
public class LimpiezaDePedidosIdempotentes {

    static final Duration VIGENCIA = Duration.ofHours(24);

    private static final Logger LOG = Logger.getLogger(LimpiezaDePedidosIdempotentes.class.getName());

    @Inject
    private PedidoIdempotenteDAO pedidos;

    @Schedule(hour = "*", minute = "41", persistent = false)
    void borrarVencidos() {
        int borrados = pedidos.borrarAnterioresA(LocalDateTime.now().minus(VIGENCIA));
        if (borrados > 0) {
            LOG.info("Se borraron " + borrados + " respuestas guardadas por Idempotency-Key de más de 24 horas.");
        }
    }
}
