package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EventoPublicado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

@ApplicationScoped
public class EventoPublicadoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(EventoPublicado evento) {
        em.persist(evento);
    }

    /**
     * Una página de los eventos de los turnos en los que participa el usuario (como paciente o como profesional),
     * del más nuevo al más viejo. La base devuelve sólo esa página: la consulta no crece con el historial.
     */
    public List<EventoPublicado> buscarPaginaDeUsuario(Long usuarioId, int desde, int cantidad) {
        return em.createQuery(
                        "SELECT e FROM EventoPublicado e WHERE e.pacienteId = :usuarioId "
                                + "OR e.profesionalId = :usuarioId ORDER BY e.publicadoEn DESC, e.idEvento", EventoPublicado.class)
                .setParameter("usuarioId", usuarioId)
                .setFirstResult(desde)
                .setMaxResults(cantidad)
                .getResultList();
    }

    public int contarDeUsuario(Long usuarioId) {
        return em.createQuery(
                        "SELECT COUNT(e) FROM EventoPublicado e WHERE e.pacienteId = :usuarioId OR e.profesionalId = :usuarioId",
                        Long.class)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult().intValue();
    }
}
