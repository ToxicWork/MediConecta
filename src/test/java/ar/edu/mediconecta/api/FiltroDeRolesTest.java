package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.CredencialesApi;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-34: qué roles admite cada método según sus anotaciones, que el filtro revisa antes de entrar al EJB. */
class FiltroDeRolesTest {

    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    static class Recurso {
        public void deLaClase() {
        }

        @RolesAllowed("ADMINISTRATIVO")
        public void delMetodo() {
        }

        @PermitAll
        public void abierto() {
        }

        @DenyAll
        public void cerrado() {
        }
    }

    static class SinAnotaciones {
        public void cualquiera() {
        }
    }

    private static Optional<Set<String>> roles(Class<?> clase, String metodo, Class<?>... parametros) throws Exception {
        Method m = clase.getMethod(metodo, parametros);
        return FiltroDeRoles.rolesPermitidos(m, clase);
    }

    @Test
    void sinAnotacionEnElMetodoValenLosRolesDeLaClase() throws Exception {
        assertEquals(Optional.of(Set.of("PACIENTE", "PROFESIONAL")), roles(Recurso.class, "deLaClase"));
    }

    @Test
    void laAnotacionDelMetodoMandaSobreLaDeLaClase() throws Exception {
        assertEquals(Optional.of(Set.of("ADMINISTRATIVO")), roles(Recurso.class, "delMetodo"));
        assertTrue(roles(Recurso.class, "abierto").isEmpty(), "@PermitAll: cualquiera");
        assertEquals(Optional.of(Set.of()), roles(Recurso.class, "cerrado"), "@DenyAll: nadie");
    }

    @Test
    void unRecursoSinAnotacionesNoRestringe() throws Exception {
        assertTrue(roles(SinAnotaciones.class, "cualquiera").isEmpty());
    }

    @Test
    void losRecursosDeLaApiDeclaranLosRolesQueEsperaElFiltro() throws Exception {
        assertEquals(Optional.of(Set.of("PACIENTE", "PROFESIONAL")), roles(TurnosResource.class, "misTurnos"));
        assertEquals(Optional.of(Set.of("PACIENTE", "PROFESIONAL", "ADMINISTRATIVO")),
                roles(TurnosResource.class, "cancelar", Long.class, String.class, String.class));
        assertTrue(roles(AuthResource.class, "login", CredencialesApi.class, UriInfo.class).isEmpty());
        assertTrue(roles(ProfesionalesResource.class, "listar").isEmpty());
    }
}
