package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.RegistroAcceso;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

@ApplicationScoped
public class RegistroAccesoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(RegistroAcceso registro) {
        em.persist(registro);
    }

    /** Accesos de terceros a un recurso (excluye los del propio titular, que no aportan a la trazabilidad). */
    public List<Object[]> buscarDeRecursoConNombre(String recurso, Long recursoId, String excluirUsuario, int maximo) {
        return em.createQuery(
                        "SELECT r, u.nombreCompleto FROM RegistroAcceso r, Usuario u WHERE u.email = r.usuario "
                                + "AND r.recurso = :recurso AND r.recursoId = :recursoId AND r.usuario <> :excluir "
                                + "ORDER BY r.fecha DESC", Object[].class)
                .setParameter("recurso", recurso)
                .setParameter("recursoId", recursoId)
                .setParameter("excluir", excluirUsuario)
                .setMaxResults(maximo)
                .getResultList();
    }
}
