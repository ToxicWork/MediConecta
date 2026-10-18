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

    /** Eventos de los turnos en los que participa el usuario, como paciente o como profesional. */
    public List<EventoPublicado> buscarRecientesDeUsuario(Long usuarioId, int maximo) {
        return em.createQuery(
                        "SELECT e FROM EventoPublicado e WHERE e.pacienteId = :usuarioId "
                                + "OR e.profesionalId = :usuarioId ORDER BY e.publicadoEn DESC", EventoPublicado.class)
                .setParameter("usuarioId", usuarioId)
                .setMaxResults(maximo)
                .getResultList();
    }
}
