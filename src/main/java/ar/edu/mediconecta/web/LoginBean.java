package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import ar.edu.mediconecta.negocio.excepciones.EmailYaRegistradoException;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
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

@Named
@SessionScoped
public class LoginBean implements Serializable {

    @Inject
    private SecurityContext securityContext;

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    private String email;
    private String password;

    private String regNombreCompleto;
    private String regEmail;
    private String regPassword;
    private String regObraSocial;
    private String regMatricula;
    private String regEspecialidad;
    private Rol rolRegistro = Rol.PACIENTE;

    public String login() {
        AuthenticationStatus status = securityContext.authenticate(
                getRequest(), getResponse(),
                AuthenticationParameters.withParams()
                        .credential(new UsernamePasswordCredential(email, new Password(password))));

        if (status == AuthenticationStatus.SUCCESS) {
            return "agenda.xhtml?faces-redirect=true";
        }
        FacesContext.getCurrentInstance().addMessage(null,
                new FacesMessage(FacesMessage.SEVERITY_ERROR, "Credenciales inválidas", null));
        return null;
    }

    public String registrar() {
        try {
            DatosRegistro datos = new DatosRegistro(regNombreCompleto, regEmail, regObraSocial, regMatricula, regEspecialidad);
            servicioDeUsuarios.registrar(rolRegistro, datos, regPassword);
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Registro exitoso, ya podés iniciar sesión", null));
            return "login.xhtml?faces-redirect=true";
        } catch (EmailYaRegistradoException e) {
            FacesContext.getCurrentInstance().addMessage(null,
                    new FacesMessage(FacesMessage.SEVERITY_ERROR, e.getMessage(), null));
            return null;
        }
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

    public Usuario getUsuarioActual() {
        var principal = getRequest().getUserPrincipal();
        if (principal == null) {
            return null;
        }
        return servicioDeUsuarios.buscarPorEmail(principal.getName()).orElse(null);
    }

    public boolean isProfesional() {
        return getRequest().isUserInRole("PROFESIONAL");
    }

    public boolean isPaciente() {
        return getRequest().isUserInRole("PACIENTE");
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

    public String getRegNombreCompleto() {
        return regNombreCompleto;
    }

    public void setRegNombreCompleto(String regNombreCompleto) {
        this.regNombreCompleto = regNombreCompleto;
    }

    public String getRegEmail() {
        return regEmail;
    }

    public void setRegEmail(String regEmail) {
        this.regEmail = regEmail;
    }

    public String getRegPassword() {
        return regPassword;
    }

    public void setRegPassword(String regPassword) {
        this.regPassword = regPassword;
    }

    public String getRegObraSocial() {
        return regObraSocial;
    }

    public void setRegObraSocial(String regObraSocial) {
        this.regObraSocial = regObraSocial;
    }

    public String getRegMatricula() {
        return regMatricula;
    }

    public void setRegMatricula(String regMatricula) {
        this.regMatricula = regMatricula;
    }

    public String getRegEspecialidad() {
        return regEspecialidad;
    }

    public void setRegEspecialidad(String regEspecialidad) {
        this.regEspecialidad = regEspecialidad;
    }

    public Rol getRolRegistro() {
        return rolRegistro;
    }

    public void setRolRegistro(Rol rolRegistro) {
        this.rolRegistro = rolRegistro;
    }
}
