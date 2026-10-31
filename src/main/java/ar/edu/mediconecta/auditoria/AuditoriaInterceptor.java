package ar.edu.mediconecta.auditoria;

import ar.edu.mediconecta.negocio.excepciones.AccesoDenegadoException;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.security.enterprise.SecurityContext;

import java.security.Principal;
import java.util.Arrays;

/**
 * Intercepting Filter (Core J2EE Patterns), implementado como interceptor CDI: envuelve cada operación
 * {@link Auditado}, deja pasar la llamada y registra quién accedió, a qué recurso y con qué resultado.
 * Agregar auditoría a una operación es poner una anotación, y no depende de por dónde llegue la llamada
 * (JSF, API REST o mensajería), a diferencia de un filtro HTTP.
 */
@Auditado
@Interceptor
@Priority(Interceptor.Priority.APPLICATION)
public class AuditoriaInterceptor {

    @Inject
    private SecurityContext seguridad;

    @Inject
    private ServicioDeRegistroAccesos registro;

    @AroundInvoke
    public Object auditar(InvocationContext contexto) throws Exception {
        Auditado marca = contexto.getMethod().getAnnotation(Auditado.class);
        if (marca == null) {
            marca = contexto.getMethod().getDeclaringClass().getAnnotation(Auditado.class);
        }
        String recurso = marca == null || marca.recurso().isBlank()
                ? contexto.getMethod().getDeclaringClass().getSimpleName() : marca.recurso();
        Principal principal = seguridad.getCallerPrincipal();
        String usuario = principal == null ? "sistema" : principal.getName();
        String operacion = contexto.getMethod().getName();
        Long recursoId = Arrays.stream(contexto.getParameters())
                .filter(Long.class::isInstance).map(Long.class::cast).findFirst().orElse(null);
        try {
            Object resultado = contexto.proceed();
            registro.registrar(usuario, operacion, recurso, recursoId, "PERMITIDO", null);
            return resultado;
        } catch (AccesoDenegadoException denegado) {
            registro.registrar(usuario, operacion, recurso, recursoId, "DENEGADO", denegado.getMessage());
            throw denegado;
        } catch (Exception error) {
            registro.registrar(usuario, operacion, recurso, recursoId, "ERROR", error.getClass().getSimpleName());
            throw error;
        }
    }
}
