import jakarta.jms.JMSContext;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.JMSSecurityException;
import jakarta.jms.JMSSecurityRuntimeException;
import java.util.function.Consumer;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

/**
 * Un cliente JMS de afuera de la aplicación, para prueba_permisos_broker.py. Se conecta al broker de WildFly por el
 * puerto HTTP, como cualquier cliente remoto, e intenta cada operación sobre los destinos de MediConecta. Imprime una
 * línea por operación: "PERMITIDO <operación>" o "DENEGADO <operación>: <motivo>" (o "ERROR" si falló por otra cosa).
 *
 * <p>Si una operación que debería estar prohibida pasa, el mensaje vence en 1 ms (va a la ExpiryQueue sin que
 * lo procese nadie, marcado con la propiedad prueba='permisos-broker') y la suscripción durable se borra enseguida.
 *
 * <p>Uso: java -cp WILDFLY_HOME/bin/client/jboss-client.jar ClienteJmsExterno.java [host:puerto]. El usuario y la
 * clave llegan por las variables de entorno CLIENTE_JMS_USUARIO y CLIENTE_JMS_CLAVE (sin ellas, se conecta anónimo);
 * la clave nunca se imprime.
 */
public class ClienteJmsExterno {

    private static final String SUSCRIPCION_INTRUSA = "prueba-permisos-intrusa";

    public static void main(String[] args) {
        String hostPuerto = args.length > 0 ? args[0] : "127.0.0.1:8080";
        String usuario = System.getenv("CLIENTE_JMS_USUARIO");
        String clave = System.getenv("CLIENTE_JMS_CLAVE");
        String url = "tcp://" + hostPuerto + "?httpUpgradeEnabled=true&httpUpgradeEndpoint=http-acceptor"
                + "&enable1xPrefixes=true&reconnectAttempts=0&callTimeout=10000&consumerWindowSize=0";
        try (ActiveMQConnectionFactory fabrica = new ActiveMQConnectionFactory(url)) {
            Cliente cliente = new Cliente(fabrica, usuario, clave);
            cliente.intentar("conectarse", jms -> { });
            cliente.intentar("publicar en el tópico turnos", jms ->
                    productor(jms).send(jms.createTopic("turnos"), "prueba de permisos"));
            cliente.intentar("suscribirse al tópico turnos", jms -> jms.createConsumer(jms.createTopic("turnos")).close());
            cliente.intentar("crear una suscripción durable al tópico turnos", jms -> {
                jms.createSharedDurableConsumer(jms.createTopic("turnos"), SUSCRIPCION_INTRUSA).close();
                jms.unsubscribe(SUSCRIPCION_INTRUSA);
            });
            cliente.intentar("leer la cola autorizaciones", jms -> jms.createConsumer(jms.createQueue("autorizaciones")).close());
            cliente.intentar("mandar a la cola autorizaciones", jms ->
                    productor(jms).send(jms.createQueue("autorizaciones"), "prueba de permisos"));
            cliente.intentar("leer la DLQ", jms -> jms.createConsumer(jms.createQueue("DLQ")).close());
        }
    }

    /** Vence en 1 ms y va marcado (prueba='permisos-broker'), para encontrarlo y borrarlo si llegara a pasar. */
    private static jakarta.jms.JMSProducer productor(JMSContext jms) {
        return jms.createProducer().setTimeToLive(1).setProperty("prueba", "permisos-broker");
    }

    private record Cliente(ActiveMQConnectionFactory fabrica, String usuario, String clave) {

        /** Cada operación en su propia conexión: un rechazo no deja la siguiente con la sesión cerrada. */
        void intentar(String operacion, Consumer<JMSContext> accion) {
            try (JMSContext jms = usuario == null ? fabrica.createContext() : fabrica.createContext(usuario, clave)) {
                accion.accept(jms);
                System.out.println("PERMITIDO " + operacion);
            } catch (JMSSecurityRuntimeException e) {
                System.out.println("DENEGADO " + operacion + ": " + motivo(e));
            } catch (JMSRuntimeException e) {
                boolean deSeguridad = e.getCause() instanceof JMSSecurityException;
                System.out.println((deSeguridad ? "DENEGADO " : "ERROR ") + operacion + ": " + motivo(e));
            }
            System.out.flush();
        }

        private static String motivo(Exception e) {
            String texto = String.valueOf(e.getMessage()).lines().findFirst().orElse("").trim();
            return texto.length() > 200 ? texto.substring(0, 200) : texto;
        }
    }
}
