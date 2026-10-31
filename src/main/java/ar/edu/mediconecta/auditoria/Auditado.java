package ar.edu.mediconecta.auditoria;

import jakarta.enterprise.util.Nonbinding;
import jakarta.interceptor.InterceptorBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marca una operación sobre datos sensibles para que {@link AuditoriaInterceptor} registre cada acceso. */
@Inherited
@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Auditado {

    /** Nombre del recurso auditado (por ejemplo, "historia_clinica"). */
    @Nonbinding
    String recurso() default "";
}
