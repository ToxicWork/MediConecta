package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.security.enterprise.AuthenticationStatus;
import jakarta.security.enterprise.SecurityContext;
import jakarta.security.enterprise.authentication.mechanism.http.AuthenticationParameters;
import jakarta.security.enterprise.credential.Password;
import jakarta.security.enterprise.credential.UsernamePasswordCredential;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.Serializable;
import java.util.Optional;

/**
 * Sesión del usuario: login, logout y el control de acceso de las vistas. El alta de pacientes está en
 * {@link RegistroBean} (pantalla propia) y el alta de otros roles, en {@link UsuariosBean} (administrativo).
 */
@Named
@SessionScoped
public class LoginBean implements Serializable {

    private static final String USUARIO_ACTUAL = LoginBean.class.getName() + ".usuarioActual";
    static final String PERFIL = "perfil.xhtml";

    @Inject
    private SecurityContext securityContext;

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    private String email;
    private String password;

    /** Con la sesión ya iniciada, el login no tiene sentido: se va a la página de inicio (para cambiar de usuario, cerrar sesión). */
    public String redirigirSiYaInicioSesion() {
        return getUsuarioActual() == null ? null : getPaginaDeInicio() + "?faces-redirect=true";
    }

    public String login() {
        if (getRequest().getUserPrincipal() != null) {
            // Si quedó otra identidad en la sesión, se descarta: si no, el contenedor seguiría usando la anterior.
            try {
                getRequest().logout();
            } catch (jakarta.servlet.ServletException e) {
                // no había identidad que descartar
            }
        }
        AuthenticationStatus status = securityContext.authenticate(
                getRequest(), getResponse(),
                AuthenticationParameters.withParams()
                        .credential(new UsernamePasswordCredential(email, new Password(password))));

        if (status == AuthenticationStatus.SUCCESS) {
            password = null;
            Optional<Usuario> usuario = servicioDeUsuarios.buscarPorEmail(email);
            if (usuario.map(Usuario::isDebeCambiarPassword).orElse(false)) {
                return PERFIL + "?faces-redirect=true";
            }
            return paginaDeInicio(usuario.map(Usuario::getRol).orElse(Rol.PACIENTE)) + "?faces-redirect=true";
        }
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Credenciales inválidas", null));
        return null;
    }

    public String logout() {
        try {
            getRequest().logout();
        } catch (jakarta.servlet.ServletException e) {
            // sin sesión activa que cerrar; se ignora
        }
        getRequest().getSession().invalidate();
        return "login.xhtml?faces-redirect=true";
    }

    /**
     * JSF evalúa este getter varias veces por página (layout, vistas, otros beans): el
     * resultado se guarda en el request para consultar la base una sola vez por request.
     */
    @SuppressWarnings("unchecked")
    public Usuario getUsuarioActual() {
        HttpServletRequest request = getRequest();
        var principal = request.getUserPrincipal();
        if (principal == null) {
            return null;
        }
        var usuario = (Optional<Usuario>) request.getAttribute(USUARIO_ACTUAL);
        if (usuario == null) {
            usuario = servicioDeUsuarios.buscarPorEmail(principal.getName());
            request.setAttribute(USUARIO_ACTUAL, usuario);
        }
        return usuario.orElse(null);
    }

    /** Después de cambiar la contraseña, el request ya no debe usar el usuario que había leído antes. */
    void olvidarUsuarioDelRequest() {
        getRequest().removeAttribute(USUARIO_ACTUAL);
    }

    public boolean isProfesional() {
        return getRequest().isUserInRole("PROFESIONAL");
    }

    public boolean isPaciente() {
        return getRequest().isUserInRole("PACIENTE");
    }

    public boolean isAdministrativo() {
        return getRequest().isUserInRole("ADMINISTRATIVO");
    }

    /** Mientras tenga una contraseña temporal, el usuario sólo puede usar "Mi perfil" para cambiarla. */
    public boolean isDebeCambiarPassword() {
        Usuario usuario = getUsuarioActual();
        return usuario != null && usuario.isDebeCambiarPassword();
    }

    /**
     * Control de acceso de las vistas ({@code f:viewAction}): sin sesión, al login; con contraseña temporal, a
     * "Mi perfil" para cambiarla; con un rol que no puede ver la página, a su página de inicio. Es la contracara
     * visual del {@code @RolesAllowed} de los servicios, que sigue siendo la barrera real aunque alguien escriba
     * la URL a mano.
     */
    public String exigirRol(String... rolesPermitidos) {
        Usuario usuario = getUsuarioActual();
        if (usuario == null) {
            return "login.xhtml?faces-redirect=true";
        }
        if (usuario.isDebeCambiarPassword()) {
            return PERFIL + "?faces-redirect=true";
        }
        HttpServletRequest request = getRequest();
        for (String rol : rolesPermitidos) {
            if (request.isUserInRole(rol)) {
                return null;
            }
        }
        return getPaginaDeInicio() + "?faces-redirect=true";
    }

    /** Para "Mi perfil": alcanza con estar autenticado (cualquier rol, aun con contraseña temporal). */
    public String exigirSesion() {
        return getUsuarioActual() == null ? "login.xhtml?faces-redirect=true" : null;
    }

    public String getPaginaDeInicio() {
        Usuario usuario = getUsuarioActual();
        return paginaDeInicio(usuario == null ? Rol.PACIENTE : usuario.getRol());
    }

    /** El administrativo arranca en Pagos; pacientes y profesionales, en la Agenda. */
    private static String paginaDeInicio(Rol rol) {
        return rol == Rol.ADMINISTRATIVO ? "pagos.xhtml" : "agenda.xhtml";
    }

    private HttpServletRequest getRequest() {
        return (HttpServletRequest) FacesContext.getCurrentInstance().getExternalContext().getRequest();
    }

    private HttpServletResponse getResponse() {
        return (HttpServletResponse) FacesContext.getCurrentInstance().getExternalContext().getResponse();
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
