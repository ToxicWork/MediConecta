package ar.edu.mediconecta.api;

import jakarta.annotation.Priority;
import jakarta.annotation.security.DenyAll;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

/**
 * Autorización por rol de la API antes de entrar al recurso. Lee {@code @PermitAll}, {@code @DenyAll} y
 * {@code @RolesAllowed} del método (o, si el método no tiene, de la clase) y corta en Problem Details con 401 si el
 * pedido no está autenticado o 403 si el rol no alcanza.
 *
 * <p>Los recursos son EJB y conservan su {@code @RolesAllowed} como segunda barrera. Este filtro evita que cada
 * rechazo esperado llegue al contenedor EJB, que lo registraba en el log del servidor como un error con la traza
 * completa (D-34).
 */
@Provider
@Priority(Priorities.AUTHORIZATION)
public class FiltroDeRoles implements ContainerRequestFilter {

    @Context
    private ResourceInfo recurso;

    @Context
    private HttpServletRequest pedido;

    @Override
    public void filter(ContainerRequestContext contexto) {
        Method metodo = recurso == null ? null : recurso.getResourceMethod();
        if (metodo == null) {
            return;
        }
        Optional<Set<String>> roles = rolesPermitidos(metodo, recurso.getResourceClass());
        if (roles.isEmpty()) {
            return;
        }
        SecurityContext seguridad = contexto.getSecurityContext();
        String instancia = pedido == null ? null : pedido.getRequestURI();
        if (seguridad.getUserPrincipal() == null) {
            contexto.abortWith(Problemas.respuesta(Problemas.de(TipoDeProblema.NO_AUTENTICADO,
                    MapeadorDeExcepciones.NECESITAS_AUTENTICARTE, instancia)).build());
        } else if (roles.get().stream().noneMatch(seguridad::isUserInRole)) {
            contexto.abortWith(Problemas.respuesta(Problemas.de(TipoDeProblema.PROHIBIDO,
                    MapeadorDeExcepciones.TU_ROL_NO_ALCANZA, instancia)).build());
        }
    }

    /**
     * Los roles que admite el método: vacío si lo puede usar cualquiera ({@code @PermitAll} o sin anotaciones), un
     * conjunto vacío si no lo puede usar nadie ({@code @DenyAll}). La anotación del método manda sobre la de la clase.
     */
    static Optional<Set<String>> rolesPermitidos(Method metodo, Class<?> clase) {
        if (metodo.isAnnotationPresent(DenyAll.class)) {
            return Optional.of(Set.of());
        }
        if (metodo.isAnnotationPresent(PermitAll.class)) {
            return Optional.empty();
        }
        RolesAllowed delMetodo = metodo.getAnnotation(RolesAllowed.class);
        if (delMetodo != null) {
            return Optional.of(Set.of(delMetodo.value()));
        }
        if (clase == null || clase.isAnnotationPresent(PermitAll.class)) {
            return Optional.empty();
        }
        if (clase.isAnnotationPresent(DenyAll.class)) {
            return Optional.of(Set.of());
        }
        RolesAllowed deLaClase = clase.getAnnotation(RolesAllowed.class);
        return deLaClase == null ? Optional.empty() : Optional.of(Set.of(deLaClase.value()));
    }
}
