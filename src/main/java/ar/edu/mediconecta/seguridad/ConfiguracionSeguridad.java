package ar.edu.mediconecta.seguridad;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.security.enterprise.identitystore.DatabaseIdentityStoreDefinition;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;

/**
 * Seguridad declarativa (Unidad III): la identidad se valida contra la tabla
 * {@code usuario} y los roles asignados (PACIENTE/PROFESIONAL/ADMINISTRATIVO) se propagan al
 * contexto de seguridad de los EJB, donde {@code ServicioDeHistoriaClinicaBean}
 * los exige con {@code @RolesAllowed}. El mecanismo de autenticación en sí
 * (cómo se valida el request HTTP) está en {@link MecanismoAutenticacion}.
 */
@ApplicationScoped
@DatabaseIdentityStoreDefinition(
        dataSourceLookup = "java:/mediconectaDS",
        callerQuery = "SELECT password FROM usuario WHERE email = ?",
        groupsQuery = "SELECT rol FROM usuario WHERE email = ?",
        hashAlgorithm = Pbkdf2PasswordHash.class,
        // ADR-0021: los mismos parámetros que usan los servicios al generar los hashes (ParametrosDeHash).
        hashAlgorithmParameters = {ParametrosDeHash.ALGORITMO, ParametrosDeHash.ITERACIONES, ParametrosDeHash.SAL}
)
public class ConfiguracionSeguridad {
}
