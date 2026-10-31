package ar.edu.mediconecta.web;

import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Named;
import jakarta.servlet.http.Cookie;

import java.util.Map;
import java.util.Set;

/**
 * Tema visual (estándar, claro u oscuro). El cambio es en tiempo real del lado del cliente
 * ({@code tema.js} cambia el atributo {@code data-tema} y las variables CSS se recalculan al instante) y se
 * recuerda en una cookie; este bean sólo lee esa cookie para que el servidor dibuje ya la página con el
 * tema elegido y no haya un parpadeo al navegar. Valida contra una lista cerrada: nunca escribe en la
 * página un valor arbitrario que venga del cliente.
 */
@Named
@RequestScoped
public class TemaBean {

    public static final String COOKIE = "mediconecta-tema";
    private static final String POR_DEFECTO = "estandar";
    private static final Set<String> VALIDOS = Set.of("estandar", "claro", "oscuro");

    public String getActual() {
        Map<String, Object> cookies = FacesContext.getCurrentInstance().getExternalContext().getRequestCookieMap();
        Object cookie = cookies.get(COOKIE);
        String valor = cookie instanceof Cookie c ? c.getValue() : null;
        return valor != null && VALIDOS.contains(valor) ? valor : POR_DEFECTO;
    }
}
