package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.seguridad.TokenRevocado;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.util.List;

@ApplicationScoped
public class TokenRevocadoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    /** Idempotente: revocar dos veces el mismo token deja una sola fila. */
    public void guardar(TokenRevocado token) {
        em.merge(token);
    }

    /** Los que todavía no vencieron (los demás ya se rechazan solos por vencidos). */
    public List<TokenRevocado> vigentes(long ahora) {
        return em.createQuery("SELECT t FROM TokenRevocado t WHERE t.venceEn > :ahora", TokenRevocado.class)
                .setParameter("ahora", ahora)
                .getResultList();
    }

    public int borrarVencidos(long ahora) {
        return em.createQuery("DELETE FROM TokenRevocado t WHERE t.venceEn <= :ahora")
                .setParameter("ahora", ahora)
                .executeUpdate();
    }
}
