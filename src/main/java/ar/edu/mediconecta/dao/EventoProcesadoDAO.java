package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.mensajeria.EventoDuplicadoException;
import ar.edu.mediconecta.modelo.EventoProcesado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class EventoProcesadoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    /**
     * Marca el evento como procesado por el suscriptor, o lanza {@link EventoDuplicadoException}
     * si ya lo estaba. La consulta previa descarta los duplicados comunes sin romper la
     * transacción; si dos entregas concurrentes pasan la consulta a la vez, la clave primaria
     * hace fallar el commit de la segunda, el broker la reentrega y ahí sí se descarta.
     */
    public void registrar(String suscriptor, String idEvento) {
        if (em.find(EventoProcesado.class, EventoProcesado.clave(suscriptor, idEvento)) != null) {
            throw new EventoDuplicadoException(idEvento, suscriptor);
        }
        em.persist(new EventoProcesado(suscriptor, idEvento));
    }

    public List<EventoProcesado> buscarPorClaves(Collection<String> claves) {
        if (claves.isEmpty()) {
            return List.of();
        }
        return em.createQuery("SELECT e FROM EventoProcesado e WHERE e.clave IN :claves", EventoProcesado.class)
                .setParameter("claves", claves)
                .getResultList();
    }
}
