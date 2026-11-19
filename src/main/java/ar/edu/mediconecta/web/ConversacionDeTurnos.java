package ar.edu.mediconecta.web;

import ar.edu.mediconecta.negocio.ServicioDeTurnos;
import jakarta.ejb.NoSuchEJBException;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.io.Serializable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * La conversación de reserva de turnos ({@link ServicioDeTurnos}, un EJB {@code @Stateful}) del usuario de la sesión,
 * compartida por las pantallas que la usan (Agenda e Historia clínica).
 *
 * <p>El componente con estado vence a los 5 minutos sin uso ({@code @StatefulTimeout}): así el contenedor libera el
 * turno en espera que el paciente no confirmó (ADR-0002). Pero la pantalla puede seguir abierta, y los beans de la web
 * guardaban la referencia: vencida, cualquier llamada daba {@link NoSuchEJBException}, un error 500 en la Agenda (y en
 * la historia clínica del profesional) hasta volver a ingresar (D-43). Esta clase la renueva: si la conversación venció,
 * empieza una nueva y repite el pedido una sola vez. Lo que dependía del turno en espera responde como cuando vence el
 * plazo ("No hay ningún turno en espera para confirmar...").
 */
@SessionScoped
public class ConversacionDeTurnos implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ConversacionDeTurnos.class.getName());

    @Inject
    private Instance<ServicioDeTurnos> conversaciones;

    private ServicioDeTurnos actual;

    /** Hace el pedido sobre la conversación vigente; si venció, sobre una nueva (una sola vez). */
    public <T> T llamar(Function<ServicioDeTurnos, T> pedido) {
        if (actual == null) {
            actual = conversaciones.get();
        }
        try {
            return pedido.apply(actual);
        } catch (NoSuchEJBException vencida) {
            LOG.info("La conversación de reserva venció (5 minutos sin uso): se empieza una nueva.");
            soltar(actual);
            actual = conversaciones.get();
            return pedido.apply(actual);
        }
    }

    /** Lo mismo, para los pedidos que no devuelven nada. */
    public void ejecutar(Consumer<ServicioDeTurnos> pedido) {
        llamar(servicio -> {
            pedido.accept(servicio);
            return null;
        });
    }

    private void soltar(ServicioDeTurnos vencida) {
        try {
            conversaciones.destroy(vencida);
        } catch (RuntimeException yaNoExiste) {
            // El contenedor ya la había destruido al vencer: alcanza con soltar la referencia.
            LOG.fine("La conversación vencida ya no existía: " + yaNoExiste.getMessage());
        }
    }
}
