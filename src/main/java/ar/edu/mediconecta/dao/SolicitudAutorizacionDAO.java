package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.SolicitudAutorizacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

@ApplicationScoped
public class SolicitudAutorizacionDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(SolicitudAutorizacion solicitud) {
        em.persist(solicitud);
    }

    public SolicitudAutorizacion buscar(String idSolicitud) {
        return em.find(SolicitudAutorizacion.class, idSolicitud);
    }

    public List<SolicitudAutorizacion> buscarRecientesDeUsuario(Long usuarioId, int maximo) {
        return em.createQuery(
                        "SELECT s FROM SolicitudAutorizacion s WHERE s.pacienteId = :id OR s.profesionalId = :id "
                                + "ORDER BY s.creadaEn DESC", SolicitudAutorizacion.class)
                .setParameter("id", usuarioId)
                .setMaxResults(maximo)
                .getResultList();
    }
}
