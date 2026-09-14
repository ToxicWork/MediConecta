package ar.edu.mediconecta.negocio.excepciones;

import jakarta.ejb.ApplicationException;

/**
 * Marcada como {@code @ApplicationException} para que el contenedor EJB la trate
 * como una regla de negocio y no como una falla del sistema: sin esta anotación,
 * al escaparse de un bean {@code @Stateful} (como {@code ServicioDeTurnosBean}),
 * el contenedor la envuelve en {@code EJBException} (por lo que ningún
 * {@code catch(TurnoNoDisponibleException e)} la atrapa) y destruye la instancia
 * del bean, rompiendo el resto de la conversación de turnos en esa sesión.
 */
@ApplicationException(rollback = true)
public class TurnoNoDisponibleException extends RuntimeException {

    public TurnoNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
