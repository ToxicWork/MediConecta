package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.OrigenReintegro;
import ar.edu.mediconecta.modelo.ReintegroPendiente;
import jakarta.ejb.Local;

import java.math.BigDecimal;
import java.util.List;

/**
 * Los reintegros que la pasarela no pudo hacer en el momento (D-06 y D-41): se guardan, se reintentan solos y el
 * administrativo ve y resuelve los que necesitan a una persona.
 */
@Local
public interface ServicioDeReintegros {

    /** La pasarela no respondió: queda pendiente y se reintenta solo. En su propia transacción. */
    void registrarParaReintentar(String idPago, String referencia, Long turnoId, BigDecimal importe, String motivo,
                                 OrigenReintegro origen, String error);

    /** La pasarela respondió, pero no conoce el pago o lo rechazó: queda para conciliación manual. */
    void registrarParaConciliar(String idPago, String referencia, Long turnoId, BigDecimal importe, String motivo,
                                OrigenReintegro origen, String detalle);

    /** Lo llama el temporizador cada minuto: reintenta los pendientes a los que ya les toca. Devuelve cuántos intentó. */
    int reintentarVencidos();

    /** El administrativo lo reintenta ya, sin esperar al temporizador. Devuelve el resultado para mostrar. */
    String reintentarAhora(Long id);

    void marcarConciliado(Long id);

    List<ReintegroPendiente> abiertos();
}
