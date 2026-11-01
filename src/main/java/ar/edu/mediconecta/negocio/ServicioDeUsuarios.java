package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import jakarta.ejb.Local;

import java.util.List;
import java.util.Optional;

@Local
public interface ServicioDeUsuarios {

    /** Registro público: siempre crea un paciente, sin importar lo que diga el pedido. */
    Usuario registrarPaciente(DatosRegistro datos, String passwordPlano);

    /** Alta hecha por un administrativo, de cualquier rol, con contraseña temporal (se cambia al primer ingreso). */
    Usuario crearUsuario(Rol rol, DatosRegistro datos, String passwordTemporal);

    /** Cambio de contraseña del usuario autenticado (sólo la propia). */
    void cambiarMiPassword(String passwordActual, String passwordNueva, String repeticion);

    List<Usuario> listarUsuarios();

    Optional<Usuario> buscarPorEmail(String email);

    List<Profesional> listarProfesionales();
}
