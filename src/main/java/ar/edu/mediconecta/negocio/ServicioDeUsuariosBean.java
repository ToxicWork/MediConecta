package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.EmailYaRegistradoException;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import ar.edu.mediconecta.negocio.factory.UsuarioFactory;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Registro y autenticación de pacientes y profesionales. Sin estado conversacional:
 * cada operación se resuelve en una sola invocación, por eso es {@code @Stateless}.
 */
@Stateless
public class ServicioDeUsuariosBean implements ServicioDeUsuarios {

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private UsuarioFactory usuarioFactory;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    @PostConstruct
    void init() {
        passwordHash.initialize(Collections.emptyMap());
    }

    @Override
    public Usuario registrar(Rol rol, DatosRegistro datos, String passwordPlano) {
        if (usuarioDAO.existeEmail(datos.email())) {
            throw new EmailYaRegistradoException(datos.email());
        }
        String hash = passwordHash.generate(passwordPlano.toCharArray());
        Usuario usuario = usuarioFactory.crear(rol, datos, hash);
        usuarioDAO.guardar(usuario);
        return usuario;
    }

    @Override
    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioDAO.buscarPorEmail(email);
    }

    @Override
    public List<Profesional> listarProfesionales() {
        return usuarioDAO.listarProfesionales();
    }
}
