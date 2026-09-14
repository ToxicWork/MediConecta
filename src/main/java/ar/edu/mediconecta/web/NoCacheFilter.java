package ar.edu.mediconecta.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Evita que el navegador cachee las páginas autenticadas. Sin esto, el botón
 * "atrás" puede volver a mostrar una página renderizada para OTRO usuario/rol
 * de la misma sesión de navegador (por ejemplo el panel de "Publicar
 * disponibilidad" de un profesional después de haber iniciado sesión como
 * paciente), incluso reenviando ese formulario cacheado.
 */
@WebFilter("*.xhtml")
public class NoCacheFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
            httpResponse.setHeader("Pragma", "no-cache");
            httpResponse.setDateHeader("Expires", 0);
        }
        chain.doFilter(request, response);
    }
}
