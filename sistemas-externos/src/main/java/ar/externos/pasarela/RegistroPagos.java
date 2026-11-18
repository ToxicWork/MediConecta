package ar.externos.pasarela;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * "Base de datos" de la pasarela simulada. Se guarda en un archivo del directorio de datos de WildFly
 * ({@code standalone/data/mediconecta-simuladores/pagos.bin}) después de cada cambio y se lee al desplegar: así un
 * redespliegue o un reinicio del servidor no hace olvidar los pagos, y los reintegros de turnos ya cobrados siguen
 * funcionando (D-35). Si no puede leerlo o escribirlo, sigue en memoria y lo avisa en el log.
 */
@ApplicationScoped
public class RegistroPagos {

    private static final Logger LOG = Logger.getLogger(RegistroPagos.class.getName());

    private final Map<String, Pago> porId = new ConcurrentHashMap<>();
    private final Map<String, String> porClaveIdempotencia = new ConcurrentHashMap<>();
    private Path archivo;

    /** Lo que se guarda en el archivo. */
    private record Contenido(Map<String, Pago> porId, Map<String, String> porClaveIdempotencia) implements Serializable {
    }

    @PostConstruct
    void cargar() {
        String datos = System.getProperty("jboss.server.data.dir", System.getProperty("java.io.tmpdir"));
        archivo = Path.of(datos, "mediconecta-simuladores", "pagos.bin");
        if (!Files.exists(archivo)) {
            return;
        }
        try (ObjectInputStream entrada = new ObjectInputStream(Files.newInputStream(archivo))) {
            Contenido guardado = (Contenido) entrada.readObject();
            porId.putAll(guardado.porId());
            porClaveIdempotencia.putAll(guardado.porClaveIdempotencia());
            LOG.info("Pasarela simulada: " + porId.size() + " pagos recuperados de " + archivo + ".");
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            LOG.log(Level.WARNING, "Pasarela simulada: no se pudieron leer los pagos guardados; se empieza de cero.", e);
        }
    }

    public Optional<Pago> porClave(String clave) {
        return clave == null ? Optional.empty() : Optional.ofNullable(porClaveIdempotencia.get(clave)).map(porId::get);
    }

    public void guardar(Pago pago, String claveIdempotencia) {
        porId.put(pago.getId(), pago);
        if (claveIdempotencia != null) {
            porClaveIdempotencia.put(claveIdempotencia, pago.getId());
        }
        persistir();
    }

    /** Después de cambiar un pago ya guardado (un reembolso). */
    public void actualizar(Pago pago) {
        porId.put(pago.getId(), pago);
        persistir();
    }

    public Optional<Pago> buscar(String id) {
        return Optional.ofNullable(porId.get(id));
    }

    public List<Pago> todos() {
        List<Pago> pagos = new ArrayList<>(porId.values());
        pagos.sort(Comparator.comparing(Pago::getCreadoEn).reversed());
        return pagos;
    }

    /** Escribe a un archivo temporal y lo renombra: un corte a mitad de camino nunca deja el archivo a medias. */
    private synchronized void persistir() {
        if (archivo == null) {
            return;
        }
        try {
            Files.createDirectories(archivo.getParent());
            Path temporal = archivo.resolveSibling("pagos.bin.tmp");
            try (ObjectOutputStream salida = new ObjectOutputStream(Files.newOutputStream(temporal))) {
                salida.writeObject(new Contenido(new HashMap<>(porId), new HashMap<>(porClaveIdempotencia)));
            }
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Pasarela simulada: no se pudieron guardar los pagos en " + archivo + ".", e);
        }
    }
}
