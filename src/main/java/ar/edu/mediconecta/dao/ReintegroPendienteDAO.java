package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EstadoReintegro;
import ar.edu.mediconecta.modelo.ReintegroPendiente;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class ReintegroPendienteDAO {

    private static final List<EstadoReintegro> ABIERTOS = List.of(EstadoReintegro.PENDIENTE, EstadoReintegro.CONCILIACION_MANUAL);

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(ReintegroPendiente reintegro) {
        em.persist(reintegro);
    }

    /** Bloqueado: el temporizador y el administrativo no lo reintentan los dos a la vez. */
    public ReintegroPendiente buscarParaActualizar(Long id) {
        return em.find(ReintegroPendiente.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Los pendientes a los que ya les toca otro intento. */
    public List<ReintegroPendiente> buscarVencidos(LocalDateTime ahora) {
        return em.createQuery("SELECT r FROM ReintegroPendiente r WHERE r.estado = :estado AND r.proximoIntento <= :ahora "
                        + "ORDER BY r.proximoIntento", ReintegroPendiente.class)
                .setParameter("estado", EstadoReintegro.PENDIENTE)
                .setParameter("ahora", ahora)
                .setMaxResults(50)
                .getResultList();
    }

    public List<ReintegroPendiente> buscarAbiertos() {
        return em.createQuery("SELECT r FROM ReintegroPendiente r WHERE r.estado IN :abiertos ORDER BY r.creadoEn DESC",
                        ReintegroPendiente.class)
                .setParameter("abiertos", ABIERTOS)
                .getResultList();
    }

    /** Para no registrar dos veces el mismo pago (la cancelación puede llegar repetida desde el broker). */
    public boolean existeAbiertoDePago(String idPago) {
        return em.createQuery("SELECT COUNT(r) FROM ReintegroPendiente r WHERE r.idPago = :idPago AND r.estado IN :abiertos",
                        Long.class)
                .setParameter("idPago", idPago)
                .setParameter("abiertos", ABIERTOS)
                .getSingleResult() > 0;
    }
}
