package ar.edu.mediconecta.modelo;

import jakarta.ejb.ApplicationException;

/**
 * Base de las reglas de negocio que se le informan al usuario. Es {@code @ApplicationException} (y la
 * marca se hereda): el contenedor revierte la transacción pero no envuelve la excepción en
 * {@code EJBException} ni destruye la instancia del bean, de modo que la conversación del
 * {@code @Stateful} de turnos sobrevive y el usuario puede reintentar.
 */
@ApplicationException(rollback = true)
public class ErrorDeNegocioException extends RuntimeException {

    public ErrorDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
