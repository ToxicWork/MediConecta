package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Usuario;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
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

    public boolean existeEmail(String email) {
        Long total = em.createQuery("SELECT COUNT(u) FROM Usuario u WHERE u.email = :email", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return total > 0;
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
}
