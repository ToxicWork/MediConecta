package ar.externos.pasarela;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** "Base de datos" en memoria de la pasarela simulada. */
@ApplicationScoped
public class RegistroPagos {

    private final Map<String, Pago> porId = new ConcurrentHashMap<>();
    private final Map<String, String> porClaveIdempotencia = new ConcurrentHashMap<>();

    public Optional<Pago> porClave(String clave) {
        return clave == null ? Optional.empty() : Optional.ofNullable(porClaveIdempotencia.get(clave)).map(porId::get);
    }

    public void guardar(Pago pago, String claveIdempotencia) {
        porId.put(pago.getId(), pago);
        if (claveIdempotencia != null) {
            porClaveIdempotencia.put(claveIdempotencia, pago.getId());
        }
    }

    public Optional<Pago> buscar(String id) {
        return Optional.ofNullable(porId.get(id));
    }

    public List<Pago> todos() {
        List<Pago> pagos = new ArrayList<>(porId.values());
        pagos.sort(Comparator.comparing(Pago::getCreadoEn).reversed());
        return pagos;
    }
}
