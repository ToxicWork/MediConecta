package ar.edu.mediconecta.negocio.excepciones;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;

/**
 * Regla de negocio de la agenda (turno tomado, hold vencido, etc.). Es una {@code @ApplicationException}
 * heredada de {@link ErrorDeNegocioException}: sin esa marca, al escaparse de un bean {@code @Stateful}
 * (como {@code ServicioDeTurnosBean}) el contenedor la envolvería en {@code EJBException} y destruiría la
 * instancia, rompiendo el resto de la conversación de turnos en esa sesión.
 */
public class TurnoNoDisponibleException extends ErrorDeNegocioException {

    public TurnoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
