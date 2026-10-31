package ar.edu.mediconecta.negocio.validacion;

/**
 * Chain of Responsibility: cada regla de la reserva es un eslabón. La solicitud recorre la cadena y el
 * primer eslabón que no se cumple corta con {@code ReservaInvalidaException}. Agregar o reordenar
 * reglas (por ejemplo, una que exija una obra social) no toca los servicios.
 */
public abstract class ValidadorReserva {

    private ValidadorReserva siguiente;

    /** Engancha el siguiente eslabón y lo devuelve, para armar la cadena en una sola expresión. */
    public ValidadorReserva enlazar(ValidadorReserva siguiente) {
        this.siguiente = siguiente;
        return siguiente;
    }

    public final void validar(SolicitudReserva solicitud) {
        verificar(solicitud);
        if (siguiente != null) {
            siguiente.validar(solicitud);
        }
    }

    protected abstract void verificar(SolicitudReserva solicitud);
}
