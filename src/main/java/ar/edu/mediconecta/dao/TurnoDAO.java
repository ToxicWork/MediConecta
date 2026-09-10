package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

@ApplicationScoped
public class TurnoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Turno turno) {
        em.persist(turno);
    }

    public Turno buscarPorId(Long id) {
        return em.find(Turno.class, id);
    }

    public Turno actualizar(Turno turno) {
        return em.merge(turno);
    }

    public List<Turno> buscarDisponibles(Long profesionalId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.profesional.id = :profesionalId "
                                + "AND t.estado = :estado ORDER BY t.fechaHora", Turno.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("estado", EstadoTurno.DISPONIBLE)
                .getResultList();
    }

    public List<Turno> buscarConfirmadosDePaciente(Long pacienteId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.paciente.id = :pacienteId "
                                + "AND t.estado = :estado ORDER BY t.fechaHora DESC", Turno.class)
                .setParameter("pacienteId", pacienteId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .getResultList();
    }

    public List<Turno> buscarConfirmadosDeProfesional(Long profesionalId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.profesional.id = :profesionalId "
                                + "AND t.estado = :estado ORDER BY t.fechaHora DESC", Turno.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .getResultList();
    }
}
