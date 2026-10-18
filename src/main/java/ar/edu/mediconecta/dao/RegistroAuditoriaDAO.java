package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.RegistroAuditoria;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@ApplicationScoped
public class RegistroAuditoriaDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(RegistroAuditoria registro) {
        em.persist(registro);
    }
}
