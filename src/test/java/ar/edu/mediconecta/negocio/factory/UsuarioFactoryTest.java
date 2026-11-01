package ar.edu.mediconecta.negocio.factory;

import ar.edu.mediconecta.modelo.Administrativo;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Patrón Factory: el rol elegido en el registro decide qué subclase de Usuario se crea. */
class UsuarioFactoryTest {

    private final UsuarioFactory factory = new UsuarioFactory();
    private final DatosRegistro datos = new DatosRegistro("Laura Diaz", "laura@mediconecta.test", " OSDE ", "",
            "MN 1", "Clinica");

    @Test
    void cadaRolCreaSuSubclaseConEseRol() {
        for (Rol rol : Rol.values()) {
            Usuario usuario = factory.crear(rol, datos, "hash");
            assertEquals(rol, usuario.getRol(), "rol de " + usuario.getClass().getSimpleName());
            assertEquals("laura@mediconecta.test", usuario.getEmail());
        }
    }

    @Test
    void administrativo() {
        assertInstanceOf(Administrativo.class, factory.crear(Rol.ADMINISTRATIVO, datos, "hash"));
    }

    @Test
    void pacienteNormalizaLosDatosOpcionales() {
        Paciente paciente = assertInstanceOf(Paciente.class, factory.crear(Rol.PACIENTE, datos, "hash"));
        assertEquals("OSDE", paciente.getObraSocial());
        assertNull(paciente.getNroAfiliado());
    }

    @Test
    void profesional() {
        Profesional profesional = assertInstanceOf(Profesional.class, factory.crear(Rol.PROFESIONAL, datos, "hash"));
        assertEquals("MN 1", profesional.getMatricula());
    }
}
