package ar.edu.mediconecta.negocio.factory;

import ar.edu.mediconecta.modelo.Administrativo;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UsuarioFactory {

    public Usuario crear(Rol rol, DatosRegistro datos, String passwordHasheado) {
        return switch (rol) {
            case PACIENTE -> new Paciente(datos.nombreCompleto(), datos.email(), passwordHasheado,
                    vacioANull(datos.obraSocial()), vacioANull(datos.nroAfiliado()));
            case PROFESIONAL -> new Profesional(datos.nombreCompleto(), datos.email(), passwordHasheado,
                    datos.matricula(), datos.especialidad());
            case ADMINISTRATIVO -> new Administrativo(datos.nombreCompleto(), datos.email(), passwordHasheado);
        };
    }

    private static String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
