package ar.edu.mediconecta.web;

import jakarta.faces.FacesException;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * D-46: con la sesión vencida (la página quedó abierta y la sesión se cerró), cualquier botón daba la página de error
 * genérica, "Algo salió mal de nuestro lado", y en los botones AJAX (como "Ver ficha") no pasaba nada. Jakarta Faces
 * avisa con una {@link ViewExpiredException}; este manejador la atrapa antes que la página de error y lleva a
 * {@value #PAGINA}, que explica qué pasó, tanto en un envío común como en uno AJAX (la redirección de Faces la
 * entienden los dos). Las demás excepciones siguen su camino de siempre. Se registra en {@code faces-config.xml}.
 */
public class ManejadorDeSesionVencida extends ExceptionHandlerWrapper {

    static final String PAGINA = "/sesion-vencida.xhtml";
    private static final Logger LOG = Logger.getLogger(ManejadorDeSesionVencida.class.getName());

    public ManejadorDeSesionVencida(ExceptionHandler envuelto) {
        super(envuelto);
    }

    @Override
    public void handle() {
        Iterator<ExceptionQueuedEvent> pendientes = getUnhandledExceptionQueuedEvents().iterator();
        while (pendientes.hasNext()) {
            ExceptionQueuedEventContext evento = (ExceptionQueuedEventContext) pendientes.next().getSource();
            if (esSesionVencida(evento.getException())) {
                pendientes.remove();
                redirigir(evento.getContext());
                break;
            }
        }
        getWrapped().handle();
    }

    /** La {@link ViewExpiredException}, aunque llegue envuelta (Faces la envuelve en una {@link FacesException}). */
    static boolean esSesionVencida(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof ViewExpiredException) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }

    static void redirigir(FacesContext contexto) {
        ExternalContext externo = contexto.getExternalContext();
        try {
            LOG.fine("Sesión vencida: se lleva al usuario a la página que lo explica.");
            externo.redirect(externo.getRequestContextPath() + PAGINA);
            contexto.responseComplete();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo redirigir a la página de sesión vencida.", e);
        }
    }

    /** La fábrica que registra {@code faces-config.xml}: envuelve al manejador de siempre. */
    public static class Fabrica extends ExceptionHandlerFactory {

        public Fabrica(ExceptionHandlerFactory envuelta) {
            super(envuelta);
        }

        @Override
        public ExceptionHandler getExceptionHandler() {
            return new ManejadorDeSesionVencida(getWrapped().getExceptionHandler());
        }
    }
}
