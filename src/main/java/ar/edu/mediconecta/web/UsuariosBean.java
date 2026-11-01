package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import ar.edu.mediconecta.negocio.excepciones.EmailYaRegistradoException;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.util.List;

/**
 * Pantalla Usuarios del administrativo: alta de pacientes, profesionales y administrativos con contraseña
 * temporal, y listado. {@code @ViewScoped} porque los campos dependen del rol elegido (se re-renderizan por
 * AJAX) y tienen que seguir existiendo cuando se envía el formulario.
 */
@Named
@ViewScoped
public class UsuariosBean implements Serializable {

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Inject
    private LoginBean loginBean;

    private Rol rol = Rol.PROFESIONAL;
    private String nombreCompleto;
    private String email;
    private String passwordTemporal;
    private String obraSocial;
    private String nroAfiliado;
    private String matricula;
    private String especialidad;
    private transient List<Usuario> usuarios;

    public String verificarAcceso() {
        return loginBean.exigirRol("ADMINISTRATIVO");
    }

    public void crear() {
        try {
            Usuario creado = servicioDeUsuarios.crearUsuario(rol,
                    new DatosRegistro(nombreCompleto, email, obraSocial, nroAfiliado, matricula, especialidad),
                    passwordTemporal);
            Mensajes.info("Creaste a " + creado.getNombreCompleto() + " (" + creado.getRol().getEtiqueta()
                    + "). Pasale su email y la contraseña temporal: la va a tener que cambiar al ingresar.");
            limpiar();
        } catch (EmailYaRegistradoException | ErrorDeNegocioException e) {
            Mensajes.error(e.getMessage());
        }
    }

    private void limpiar() {
        nombreCompleto = null;
        email = null;
        passwordTemporal = null;
        obraSocial = null;
        nroAfiliado = null;
        matricula = null;
        especialidad = null;
        usuarios = null;
    }

    public List<Usuario> getUsuarios() {
        if (usuarios == null) {
            usuarios = servicioDeUsuarios.listarUsuarios();
        }
        return usuarios;
    }

    /** Dato propio de cada rol para la tabla: obra social del paciente o matrícula y especialidad del profesional. */
    public String detalle(Usuario usuario) {
        if (usuario instanceof Paciente paciente) {
            return paciente.getObraSocial() == null ? "Particular" : paciente.getObraSocial();
        }
        if (usuario instanceof Profesional profesional) {
            return profesional.getMatricula() + " · " + profesional.getEspecialidad();
        }
        return "—";
    }

    public Rol[] getRoles() {
        return Rol.values();
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
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

    public String getPasswordTemporal() {
        return passwordTemporal;
    }

    public void setPasswordTemporal(String passwordTemporal) {
        this.passwordTemporal = passwordTemporal;
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

    public String getMatricula() {
        return matricula;
    }

    public void setMatricula(String matricula) {
        this.matricula = matricula;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }
}
