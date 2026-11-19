package ar.edu.mediconecta.web;

import jakarta.faces.FacesException;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerFactory;
import jakarta.faces.context.ExternalContext;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.faces.event.ExceptionQueuedEventContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D-46: con la sesión vencida se va a la página que lo explica, también desde un botón AJAX. */
class ManejadorDeSesionVencidaTest {

    private final FacesContext contexto = mock(FacesContext.class);
    private final ExternalContext externo = mock(ExternalContext.class);
    private final ExceptionHandler envuelto = mock(ExceptionHandler.class);
    private final List<ExceptionQueuedEvent> pendientes = new ArrayList<>();

    @BeforeEach
    void armar() {
        when(contexto.getExternalContext()).thenReturn(externo);
        when(externo.getRequestContextPath()).thenReturn("/mediconecta");
        when(envuelto.getUnhandledExceptionQueuedEvents()).thenReturn(pendientes);
    }

    private void encolar(Throwable error) {
        pendientes.add(new ExceptionQueuedEvent(contexto, new ExceptionQueuedEventContext(contexto, error)));
    }

    @Test
    void reconoceLaSesionVencidaAunqueLlegueEnvuelta() {
        assertTrue(ManejadorDeSesionVencida.esSesionVencida(new ViewExpiredException("vista vencida")));
        assertTrue(ManejadorDeSesionVencida.esSesionVencida(new FacesException(new ViewExpiredException("vista vencida"))));
        assertFalse(ManejadorDeSesionVencida.esSesionVencida(new IllegalStateException("otra cosa")));
        assertFalse(ManejadorDeSesionVencida.esSesionVencida(null));
    }

    @Test
    void conLaSesionVencidaLlevaALaPaginaQueLoExplica() throws IOException {
        encolar(new FacesException(new ViewExpiredException("vista vencida")));

        new ManejadorDeSesionVencida(envuelto).handle();

        verify(externo).redirect("/mediconecta/sesion-vencida.xhtml");
        verify(contexto).responseComplete();
        assertTrue(pendientes.isEmpty(), "ya no la maneja la página de error genérica");
        verify(envuelto).handle();
    }

    @Test
    void lasDemasExcepcionesSiguenSuCamino() throws IOException {
        encolar(new IllegalStateException("falla real"));

        new ManejadorDeSesionVencida(envuelto).handle();

        verify(externo, never()).redirect(anyString());
        assertEquals(1, pendientes.size());
        verify(envuelto).handle();
    }

    @Test
    void siNoPuedeRedirigirLoAvisa() throws IOException {
        doThrow(new IOException("respuesta ya enviada")).when(externo).redirect(anyString());

        assertThrows(UncheckedIOException.class, () -> ManejadorDeSesionVencida.redirigir(contexto));
    }

    @Test
    void laFabricaEnvuelveAlManejadorDeSiempre() {
        ExceptionHandlerFactory original = mock(ExceptionHandlerFactory.class);
        when(original.getExceptionHandler()).thenReturn(envuelto);

        ExceptionHandler manejador = new ManejadorDeSesionVencida.Fabrica(original).getExceptionHandler();

        assertInstanceOf(ManejadorDeSesionVencida.class, manejador);
        assertEquals(envuelto, ((ManejadorDeSesionVencida) manejador).getWrapped());
    }
}
