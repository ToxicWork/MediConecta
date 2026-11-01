package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EstadoReclamo;
import ar.edu.mediconecta.modelo.ReclamoObraSocial;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ReclamoObraSocialDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(ReclamoObraSocial reclamo) {
        em.persist(reclamo);
    }

    public Optional<ReclamoObraSocial> buscarVigenteDeTurno(Long turnoId) {
        return em.createQuery("SELECT r FROM ReclamoObraSocial r WHERE r.turno.id = :turnoId AND r.estado = :estado",
                        ReclamoObraSocial.class)
                .setParameter("turnoId", turnoId)
                .setParameter("estado", EstadoReclamo.PENDIENTE_PRESENTACION)
                .getResultStream()
                .findFirst();
    }

    public List<ReclamoObraSocial> buscarTodos() {
        return em.createQuery("SELECT r FROM ReclamoObraSocial r ORDER BY r.creadoEn DESC", ReclamoObraSocial.class)
                .getResultList();
    }
}
