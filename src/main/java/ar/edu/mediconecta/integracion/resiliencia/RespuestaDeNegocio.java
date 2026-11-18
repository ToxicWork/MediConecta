package ar.edu.mediconecta.integracion.resiliencia;

/**
 * Marca las excepciones que son una respuesta del sistema externo, no una falla: por ejemplo, un SOAP Fault de negocio
 * (el legado funciona, pero rechaza el pedido por sus datos). Para el {@link CircuitBreaker} cuentan como un éxito: el
 * sistema respondió, así que el circuito se cierra (o sigue cerrado) y el contador de fallas vuelve a cero.
 */
public interface RespuestaDeNegocio {
}
