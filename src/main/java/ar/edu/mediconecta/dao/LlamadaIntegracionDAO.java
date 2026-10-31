package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.LlamadaIntegracion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class LlamadaIntegracionDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(LlamadaIntegracion llamada) {
        em.persist(llamada);
    }

    public List<LlamadaIntegracion> buscarRecientes(int maximo) {
        return em.createQuery("SELECT l FROM LlamadaIntegracion l ORDER BY l.ocurridaEn DESC", LlamadaIntegracion.class)
                .setMaxResults(maximo)
                .getResultList();
    }

    /** Cantidad de llamadas fallidas por referencia (por ejemplo, los intentos de una solicitud de autorización). */
    public Map<String, Long> contarFallasPorReferencia(Collection<String> referencias) {
        if (referencias.isEmpty()) {
            return Map.of();
        }
        return em.createQuery("SELECT l.referencia, COUNT(l) FROM LlamadaIntegracion l WHERE l.referencia IN :refs "
                        + "AND l.resultado <> 'OK' GROUP BY l.referencia", Object[].class)
                .setParameter("refs", referencias)
                .getResultList().stream()
                .collect(Collectors.toMap(f -> (String) f[0], f -> (Long) f[1]));
    }
}
