package ar.externos.videoconsulta;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class RegistroSalas {

    private final Map<String, Sala> salas = new ConcurrentHashMap<>();

    public void guardar(Sala sala) {
        salas.put(sala.getId(), sala);
    }

    public Optional<Sala> buscar(String id) {
        return Optional.ofNullable(salas.get(id));
    }

    public List<Sala> todas() {
        List<Sala> lista = new ArrayList<>(salas.values());
        lista.sort(Comparator.comparing(Sala::getCreadaEn).reversed());
        return lista;
    }
}
