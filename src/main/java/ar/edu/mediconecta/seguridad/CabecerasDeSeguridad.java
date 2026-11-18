package ar.edu.mediconecta.seguridad;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cabeceras de seguridad en todas las respuestas (Eje D: «CSP, HSTS, X-Content-Type-Options presentes»).
 *
 * <ul>
 *   <li>{@code Strict-Transport-Security}, sólo por HTTPS. Dura {@value #HSTS_POR_DEFECTO} segundos salvo que se
 *       configure {@code -Dmediconecta.hsts.segundos}: el navegador la guarda para todo {@code localhost}, sin
 *       importar el puerto, y un año rompería cualquier otro proyecto local que se use por HTTP. En producción va
 *       un año ({@code 31536000}).</li>
 *   <li>{@code X-Content-Type-Options: nosniff}, {@code X-Frame-Options: DENY}, {@code Referrer-Policy:
 *       same-origin} y una {@code Permissions-Policy} que niega cámara, micrófono, ubicación y pagos: la app no los
 *       usa (la sala de videoconsulta es del proveedor, en otra pestaña).</li>
 *   <li>{@code Content-Security-Policy}: en las páginas la pone PrimeFaces, con un nonce por respuesta para los
 *       scripts ({@code primefaces.CSP} en web.xml); acá sólo la de la API, que no carga nada:
 *       {@code default-src 'none'}. La API además responde {@code Cache-Control: no-store}: sus respuestas tienen
 *       datos personales.</li>
 * </ul>
 * La API no publica cabeceras CORS: sólo la usa el mismo origen (y nunca con comodín y credenciales).
 */
@WebFilter(filterName = "CabecerasDeSeguridad", urlPatterns = "/*", asyncSupported = true)
public class CabecerasDeSeguridad implements Filter {

    static final long HSTS_POR_DEFECTO = 3600;
    static final String CSP_DE_LA_API = "default-src 'none'; frame-ancestors 'none'";

    private long hstsSegundos = HSTS_POR_DEFECTO;

    @Override
    public void init(FilterConfig configuracion) {
        hstsSegundos = Long.getLong("mediconecta.hsts.segundos", HSTS_POR_DEFECTO);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest pedido && response instanceof HttpServletResponse respuesta) {
            boolean esApi = pedido.getRequestURI().startsWith(pedido.getContextPath() + "/api/");
            cabeceras(esApi, pedido.isSecure(), hstsSegundos).forEach(respuesta::setHeader);
        }
        chain.doFilter(request, response);
    }

    /** Las cabeceras de una respuesta, según sea de la API o no y haya llegado por HTTPS o no. */
    static Map<String, String> cabeceras(boolean esApi, boolean https, long hstsSegundos) {
        Map<String, String> cabeceras = new LinkedHashMap<>();
        if (https) {
            cabeceras.put("Strict-Transport-Security", "max-age=" + hstsSegundos);
        }
        cabeceras.put("X-Content-Type-Options", "nosniff");
        cabeceras.put("X-Frame-Options", "DENY");
        cabeceras.put("Referrer-Policy", "same-origin");
        cabeceras.put("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        cabeceras.put("Cross-Origin-Opener-Policy", "same-origin");
        if (esApi) {
            cabeceras.put("Content-Security-Policy", CSP_DE_LA_API);
            cabeceras.put("Cache-Control", "no-store");
        }
        return cabeceras;
    }
}
