package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EstadoReclamo;
import ar.edu.mediconecta.modelo.ReclamoObraSocial;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityGraph;
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

    /**
     * Los listados traen cada reclamo con su turno, su paciente y su profesional en la misma consulta (un entity graph
     * de JPA; el JPQL estricto no admite alias en JOIN FETCH): la pantalla Pagos muestra los tres por fila. Sin esto,
     * cada fila leía su turno con una consulta aparte (N+1): con 355 cobros y 165 reclamos eran 518 consultas y
     * 30 s para abrir la pantalla del administrativo (D-38).
     */
    public List<ReclamoObraSocial> buscarTodos() {
        EntityGraph<ReclamoObraSocial> grafo = em.createEntityGraph(ReclamoObraSocial.class);
        grafo.addSubgraph("turno").addAttributeNodes("paciente", "profesional");
        return em.createQuery("SELECT r FROM ReclamoObraSocial r ORDER BY r.creadoEn DESC", ReclamoObraSocial.class)
                .setHint("jakarta.persistence.loadgraph", grafo)
                .getResultList();
    }
}
