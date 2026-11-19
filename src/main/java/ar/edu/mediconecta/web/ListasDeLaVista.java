package ar.edu.mediconecta.web;

import jakarta.faces.context.FacesContext;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Listas que una tabla muestra y que sus botones usan al volver (D-45).
 *
 * <p>Cuando se toca un botón de una fila, Jakarta Faces vuelve a pedir la lista y toma la fila por su posición. Si la
 * lista se vuelve a leer de la base y cambió desde que se mostró la página (otro usuario canceló un turno), la misma
 * posición es otro turno: el paciente confirmaba cancelar el de las 07:15 y se cancelaba el de las 16:30. Guardada en
 * la vista, la lista del envío es la misma que se mostró; después de cada acción que la cambia se olvida y se vuelve a
 * leer. Es para los botones con {@code p:confirm}, que no pueden mandar el id con {@code f:param} (PrimeFaces lo pierde
 * al confirmar); los demás lo mandan así.
 */
final class ListasDeLaVista {

    private ListasDeLaVista() {
    }

    @SuppressWarnings("unchecked")
    static <T> List<T> de(String clave, Supplier<List<T>> carga) {
        return (List<T>) vista().computeIfAbsent(clave, c -> carga.get());
    }

    static void olvidar(String... claves) {
        Map<String, Object> vista = vista();
        for (String clave : claves) {
            vista.remove(clave);
        }
    }

    private static Map<String, Object> vista() {
        return FacesContext.getCurrentInstance().getViewRoot().getViewMap();
    }
}
