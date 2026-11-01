package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import ar.edu.mediconecta.negocio.excepciones.EmailYaRegistradoException;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Registro público (registro.xhtml): sólo pacientes. Profesionales y administrativos los da de alta un
 * administrativo desde la pantalla Usuarios. La restricción está en el servicio, no sólo en esta vista.
 */
@Named
@RequestScoped
public class RegistroBean {

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Inject
    private LoginBean loginBean;

    private String nombreCompleto;
    private String email;
    private String password;
    private String repeticion;
    private String obraSocial;
    private String nroAfiliado;

    /** Quien ya inició sesión no tiene nada que hacer en el registro: va a su página de inicio. */
    public String verificarAcceso() {
        return loginBean.getUsuarioActual() == null ? null : loginBean.getPaginaDeInicio() + "?faces-redirect=true";
    }

    public String registrar() {
        if (password == null || !password.equals(repeticion)) {
            Mensajes.error("Las contraseñas no coinciden.");
            return null;
        }
        try {
            servicioDeUsuarios.registrarPaciente(
                    new DatosRegistro(nombreCompleto, email, obraSocial, nroAfiliado, null, null), password);
            Mensajes.infoTrasRedireccion("Registro exitoso, ya podés iniciar sesión.");
            return "login.xhtml?faces-redirect=true";
        } catch (EmailYaRegistradoException | ErrorDeNegocioException e) {
            Mensajes.error(e.getMessage());
            return null;
        }
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
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

    public String getRepeticion() {
        return repeticion;
    }

    public void setRepeticion(String repeticion) {
        this.repeticion = repeticion;
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public void setObraSocial(String obraSocial) {
        this.obraSocial = obraSocial;
    }

    public String getNroAfiliado() {
        return nroAfiliado;
    }

    public void setNroAfiliado(String nroAfiliado) {
        this.nroAfiliado = nroAfiliado;
    }
}
