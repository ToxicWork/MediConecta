package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EstadoPago;
import ar.edu.mediconecta.modelo.Pago;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PagoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Pago pago) {
        em.persist(pago);
    }

    public Optional<Pago> buscarAprobadoDeTurno(Long turnoId) {
        return em.createQuery("SELECT p FROM Pago p WHERE p.turno.id = :turnoId AND p.estado = :estado", Pago.class)
                .setParameter("turnoId", turnoId)
                .setParameter("estado", EstadoPago.APROBADO)
                .getResultStream()
                .findFirst();
    }

    public List<Pago> buscarDePaciente(Long pacienteId) {
        return conTurno(em.createQuery("SELECT p FROM Pago p WHERE p.pacienteId = :id ORDER BY p.creadoEn DESC", Pago.class)
                .setParameter("id", pacienteId));
    }

    public List<Pago> buscarTodos() {
        return conTurno(em.createQuery("SELECT p FROM Pago p ORDER BY p.creadoEn DESC", Pago.class));
    }

    /**
     * Los listados traen cada pago con su turno, su paciente y su profesional en la misma consulta (un entity graph
     * de JPA; el JPQL estricto no admite alias en JOIN FETCH): la pantalla Pagos muestra los tres por fila. Sin esto,
     * cada fila leía su turno con una consulta aparte (N+1): con 355 cobros y 165 reclamos eran 518 consultas y
     * 30 s para abrir la pantalla del administrativo (D-38).
     */
    private List<Pago> conTurno(TypedQuery<Pago> consulta) {
        EntityGraph<Pago> grafo = em.createEntityGraph(Pago.class);
        grafo.addSubgraph("turno").addAttributeNodes("paciente", "profesional");
        return consulta.setHint("jakarta.persistence.loadgraph", grafo).getResultList();
    }
}
