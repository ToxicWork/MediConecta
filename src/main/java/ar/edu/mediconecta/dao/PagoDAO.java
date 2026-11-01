package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EstadoPago;
import ar.edu.mediconecta.modelo.Pago;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

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
        return em.createQuery("SELECT p FROM Pago p WHERE p.pacienteId = :id ORDER BY p.creadoEn DESC", Pago.class)
                .setParameter("id", pacienteId)
                .getResultList();
    }

    public List<Pago> buscarTodos() {
        return em.createQuery("SELECT p FROM Pago p ORDER BY p.creadoEn DESC", Pago.class)
                .getResultList();
    }
}
