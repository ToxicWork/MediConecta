package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.PedidoIdempotente;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.Optional;

/** Pedidos a la API con {@code Idempotency-Key} y sus respuestas guardadas. */
@ApplicationScoped
public class PedidoIdempotenteDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public Optional<PedidoIdempotente> buscar(Long usuarioId, String clave) {
        return em.createQuery("select p from PedidoIdempotente p where p.usuarioId = :usuario and p.clave = :clave",
                        PedidoIdempotente.class)
                .setParameter("usuario", usuarioId)
                .setParameter("clave", clave)
                .getResultStream()
                .findFirst();
    }

    /** Lo guarda y lo escribe ya: si la clave ya estaba (dos pedidos simultáneos), falla acá y no al confirmar. */
    public void guardar(PedidoIdempotente pedido) {
        em.persist(pedido);
        em.flush();
    }

    /** Borra las respuestas guardadas antes de {@code limite}; devuelve cuántas. */
    public int borrarAnterioresA(LocalDateTime limite) {
        return em.createQuery("delete from PedidoIdempotente p where p.creadoEn < :limite")
                .setParameter("limite", limite)
                .executeUpdate();
    }
}
