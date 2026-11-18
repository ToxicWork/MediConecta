package ar.edu.mediconecta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Lo que hace el contenedor con un EJB o un bean CDI, para probarlo sin contenedor: completar sus campos
 * {@code @Inject} / {@code @Resource} y llamar a su {@code @PostConstruct}.
 */
public final class Inyectar {

    private Inyectar() {
    }

    /** Pone {@code valor} en el campo {@code nombre} de {@code objeto} (o de alguna de sus superclases). */
    public static <T> T en(T objeto, String nombre, Object valor) {
        for (Class<?> clase = objeto.getClass(); clase != null; clase = clase.getSuperclass()) {
            try {
                Field campo = clase.getDeclaredField(nombre);
                campo.setAccessible(true);
                campo.set(objeto, valor);
                return objeto;
            } catch (NoSuchFieldException seguirBuscando) {
                // en la superclase
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException(objeto.getClass().getSimpleName() + " no tiene el campo " + nombre);
    }

    /** Invoca un método sin parámetros aunque no sea público (un {@code @PostConstruct}, un temporizador). */
    public static Object invocar(Object objeto, String metodo) {
        try {
            Method m = objeto.getClass().getDeclaredMethod(metodo);
            m.setAccessible(true);
            return m.invoke(objeto);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) {
                throw r;
            }
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Lee un campo privado: para verificar el estado que deja una operación. */
    public static Object leer(Object objeto, String nombre) {
        for (Class<?> clase = objeto.getClass(); clase != null; clase = clase.getSuperclass()) {
            try {
                Field campo = clase.getDeclaredField(nombre);
                campo.setAccessible(true);
                return campo.get(objeto);
            } catch (NoSuchFieldException seguirBuscando) {
                // en la superclase
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException(objeto.getClass().getSimpleName() + " no tiene el campo " + nombre);
    }
}
