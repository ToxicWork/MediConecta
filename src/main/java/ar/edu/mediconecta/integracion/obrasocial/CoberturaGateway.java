package ar.edu.mediconecta.integracion.obrasocial;

import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Prestacion;

import java.time.LocalDateTime;

/**
 * Gateway (Fowler) / Target del patrón Adapter: la interfaz con la que el dominio habla con la obra
 * social, en sus propios términos. Hoy la implementa {@link CoberturaSoapAdapter} contra el legado SOAP;
 * si otra obra social expusiera REST, se agrega otro adaptador sin tocar los servicios de negocio.
 */
public interface CoberturaGateway {

    /**
     * Nunca lanza excepción por indisponibilidad: si el legado no responde, devuelve la última respuesta
     * vigente (caché) o una cobertura PENDIENTE (Fallback).
     */
    ResultadoCobertura validarCobertura(Paciente paciente, Prestacion prestacion, LocalDateTime fecha, String referencia);

    /**
     * Lanza {@link ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException} si el legado
     * no responde: quien consume la cola hace rollback y el broker reintenta más tarde.
     */
    ResultadoAutorizacion autorizar(PedidoAutorizacion pedido);

    EstadoCircuito estadoCircuito();
}
