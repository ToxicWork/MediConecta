package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.Notificacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

@ApplicationScoped
public class NotificacionDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Notificacion notificacion) {
        em.persist(notificacion);
    }

    public List<Notificacion> buscarDeUsuario(Long usuarioId) {
        return em.createQuery(
                        "SELECT n FROM Notificacion n WHERE n.destinatarioId = :usuarioId "
                                + "ORDER BY n.creadaEn DESC", Notificacion.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    public long contarNoLeidas(Long usuarioId) {
        return em.createQuery(
                        "SELECT COUNT(n) FROM Notificacion n WHERE n.destinatarioId = :usuarioId "
                                + "AND n.leida = false", Long.class)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult();
    }

    /**
     * Marca una notificación como leída sólo si es del usuario: la condición sobre el destinatario va en el mismo
     * UPDATE, así una notificación ajena (otro id) no se toca y no hace falta consultarla antes (D-16, BOLA).
     */
    public boolean marcarLeida(Long usuarioId, Long notificacionId) {
        return em.createQuery(
                        "UPDATE Notificacion n SET n.leida = true WHERE n.id = :id AND n.destinatarioId = :usuarioId "
                                + "AND n.leida = false")
                .setParameter("id", notificacionId)
                .setParameter("usuarioId", usuarioId)
                .executeUpdate() == 1;
    }

    public int marcarLeidas(Long usuarioId) {
        return em.createQuery(
                        "UPDATE Notificacion n SET n.leida = true WHERE n.destinatarioId = :usuarioId "
                                + "AND n.leida = false")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
