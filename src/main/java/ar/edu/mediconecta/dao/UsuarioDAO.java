package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class UsuarioDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Usuario usuario) {
        em.persist(usuario);
    }

    public Optional<Usuario> buscarPorEmail(String email) {
        return em.createQuery("SELECT u FROM Usuario u WHERE u.email = :email", Usuario.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst();
    }

    /**
     * Como {@link #buscarPorEmail}, pero bloquea la fila hasta el fin de la transacción: dos intentos de ingreso
     * simultáneos no pueden leer el mismo contador y perder uno de los fallos.
     */
    public Optional<Usuario> buscarPorEmailParaActualizar(String email) {
        return em.createQuery("SELECT u FROM Usuario u WHERE u.email = :email", Usuario.class)
                .setParameter("email", email)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultStream()
                .findFirst();
    }

    public Usuario buscarPorId(Long id) {
        return em.find(Usuario.class, id);
    }

    public boolean existeEmail(String email) {
        Long total = em.createQuery("SELECT COUNT(u) FROM Usuario u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return total > 0;
    }

    /** Para la pantalla Usuarios del administrativo: agrupados por rol y por nombre. */
    public List<Usuario> listarTodos() {
        return em.createQuery("SELECT u FROM Usuario u ORDER BY u.rol, u.nombreCompleto", Usuario.class)
                .getResultList();
    }

    public List<Profesional> listarProfesionales() {
        return em.createQuery("SELECT p FROM Profesional p ORDER BY p.nombreCompleto", Profesional.class)
                .getResultList();
    }

    public Paciente obtenerReferenciaPaciente(Long id) {
        return em.getReference(Paciente.class, id);
    }

    public Profesional obtenerReferenciaProfesional(Long id) {
        return em.getReference(Profesional.class, id);
    }

    /**
     * Bloquea la fila del usuario hasta el fin de la transacción (SELECT ... FOR UPDATE). Serializa las operaciones
     * "verificar y después insertar" de un mismo usuario, como publicar un horario con doble clic.
     */
    public void bloquear(Long usuarioId) {
        em.find(Usuario.class, usuarioId, LockModeType.PESSIMISTIC_WRITE);
    }
}
