package ar.edu.mediconecta;

import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

/** Datos de prueba sin contenedor: entidades armadas a mano, con id asignado por reflexión. */
public final class Fixtures {

    private Fixtures() {
    }

    public static Profesional profesional(long id, String especialidad) {
        return conId(new Profesional("Dra. Ana Lopez", "ana@test", "x", "MN 1", especialidad), id);
    }

    public static Paciente paciente(long id, String obraSocial) {
        return conId(new Paciente("Juan Perez", "juan@test", "x", obraSocial), id);
    }

    public static Turno turno(LocalDateTime fechaHora) {
        return new Turno(profesional(20, "Cardiología"), fechaHora);
    }

    public static <T extends Usuario> T conId(T usuario, long id) {
        try {
            Field campo = Usuario.class.getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(usuario, id);
            return usuario;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
