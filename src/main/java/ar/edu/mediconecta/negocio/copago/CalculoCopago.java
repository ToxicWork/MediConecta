package ar.edu.mediconecta.negocio.copago;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;

import java.math.BigDecimal;

/**
 * Strategy: cada obra social calcula el copago de una forma distinta (porcentaje, monto fijo, exento) y
 * el particular paga el total. CDI descubre todas las implementaciones y {@link SelectorCalculoCopago}
 * elige la que aplica a la respuesta de cobertura; sumar una regla nueva es agregar una clase.
 */
public interface CalculoCopago {

    boolean aplica(ResultadoCobertura cobertura);

    ImporteTurno calcular(BigDecimal precioLista, ResultadoCobertura cobertura);
}
