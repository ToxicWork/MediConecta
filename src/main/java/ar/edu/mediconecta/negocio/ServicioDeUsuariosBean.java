package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.DatosDeUsuarioInvalidosException;
import ar.edu.mediconecta.negocio.excepciones.EmailYaRegistradoException;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import ar.edu.mediconecta.negocio.factory.UsuarioFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Alta de usuarios, cambio de contraseña y consultas. Sin estado conversacional: cada operación se resuelve
 * en una sola invocación, por eso es {@code @Stateless}.
 *
 * <p>Seguridad declarativa por operación: el registro público ({@code @PermitAll}) sólo puede crear
 * pacientes, porque el rol no es un parámetro (no alcanza con esconder las otras opciones en la pantalla:
 * el pedido HTTP se puede armar a mano). Crear usuarios de cualquier rol y listarlos exige
 * ADMINISTRATIVO; cambiar la contraseña exige estar autenticado y sólo afecta al usuario de la sesión.
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL", "ADMINISTRATIVO"})
public class ServicioDeUsuariosBean implements ServicioDeUsuarios {

    private static final Logger LOG = Logger.getLogger(ServicioDeUsuariosBean.class.getName());

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private UsuarioFactory usuarioFactory;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    @Resource
    private SessionContext contexto;

    @PostConstruct
    void init() {
        passwordHash.initialize(Collections.emptyMap());
    }

    @Override
    @PermitAll
    public Usuario registrarPaciente(DatosRegistro datos, String passwordPlano) {
        return alta(Rol.PACIENTE, datos, passwordPlano);
    }

    @Override
    @RolesAllowed("ADMINISTRATIVO")
    public Usuario crearUsuario(Rol rol, DatosRegistro datos, String passwordTemporal) {
        if (rol == null) {
            throw new DatosDeUsuarioInvalidosException("Elegí el rol del usuario.");
        }
        if (rol == Rol.PROFESIONAL && (vacio(datos.matricula()) || vacio(datos.especialidad()))) {
            throw new DatosDeUsuarioInvalidosException("Para un profesional, completá matrícula y especialidad.");
        }
        Usuario usuario = alta(rol, datos, passwordTemporal);
        usuario.exigirCambioDePassword();
        LOG.info("Alta de usuario " + rol + " (" + usuario.getEmail() + ") por el administrativo "
                + contexto.getCallerPrincipal().getName() + ".");
        return usuario;
    }

    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL", "ADMINISTRATIVO"})
    public void cambiarMiPassword(String passwordActual, String passwordNueva, String repeticion) {
        Usuario usuario = usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName())
                .orElseThrow(() -> new DatosDeUsuarioInvalidosException("No encontramos tu usuario."));
        if (passwordActual == null || !passwordHash.verify(passwordActual.toCharArray(), usuario.getPassword())) {
            throw new DatosDeUsuarioInvalidosException("La contraseña actual no es correcta.");
        }
        PoliticaDeContrasenas.validarCambio(passwordActual, passwordNueva, repeticion);
        usuario.cambiarPassword(passwordHash.generate(passwordNueva.toCharArray()));
    }

    @Override
    @RolesAllowed("ADMINISTRATIVO")
    public List<Usuario> listarUsuarios() {
        return usuarioDAO.listarTodos();
    }

    @Override
    @PermitAll
    public Optional<Usuario> buscarPorEmail(String email) {
        return usuarioDAO.buscarPorEmail(email);
    }

    @Override
    @PermitAll
    public List<Profesional> listarProfesionales() {
        return usuarioDAO.listarProfesionales();
    }

    private Usuario alta(Rol rol, DatosRegistro datos, String passwordPlano) {
        if (vacio(datos.nombreCompleto())) {
            throw new DatosDeUsuarioInvalidosException("Completá el nombre.");
        }
        String email = datos.email() == null ? "" : datos.email().trim();
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new DatosDeUsuarioInvalidosException("Ingresá un email válido.");
        }
        PoliticaDeContrasenas.validar(passwordPlano);
        if (usuarioDAO.existeEmail(email)) {
            throw new EmailYaRegistradoException(email);
        }
        DatosRegistro normalizados = new DatosRegistro(datos.nombreCompleto().trim(), email, datos.obraSocial(),
                datos.nroAfiliado(), datos.matricula(), datos.especialidad());
        Usuario usuario = usuarioFactory.crear(rol, normalizados, passwordHash.generate(passwordPlano.toCharArray()));
        usuarioDAO.guardar(usuario);
        return usuario;
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
