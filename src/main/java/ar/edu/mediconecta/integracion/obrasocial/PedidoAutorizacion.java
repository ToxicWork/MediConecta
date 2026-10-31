package ar.edu.mediconecta.integracion.obrasocial;

import java.time.LocalDateTime;

public record PedidoAutorizacion(String idSolicitud, String obraSocial, String plan, String nroAfiliado,
                                 String codigoPrestacion, LocalDateTime fechaPrestacion, String matriculaPrestador) {
}
