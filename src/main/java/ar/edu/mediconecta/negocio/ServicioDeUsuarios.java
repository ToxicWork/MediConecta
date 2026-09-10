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

    Usuario registrar(Rol rol, DatosRegistro datos, String passwordPlano);

    Optional<Usuario> buscarPorEmail(String email);

    List<Profesional> listarProfesionales();
}
