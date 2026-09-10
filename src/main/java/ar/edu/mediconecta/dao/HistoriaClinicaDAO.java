package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EntradaClinica;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class HistoriaClinicaDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public HistoriaClinica buscarPorPaciente(Long pacienteId) {
        return em.createQuery(
                        "SELECT h FROM HistoriaClinica h WHERE h.paciente.id = :pacienteId", HistoriaClinica.class)
                .setParameter("pacienteId", pacienteId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public void guardar(HistoriaClinica historia) {
        em.persist(historia);
    }

    public void agregarEntrada(EntradaClinica entrada) {
        em.persist(entrada);
    }
}
