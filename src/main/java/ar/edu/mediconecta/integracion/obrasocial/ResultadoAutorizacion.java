package ar.edu.mediconecta.integracion.obrasocial;

public record ResultadoAutorizacion(boolean autorizada, String codigoAutorizacion, String motivo, String nroTransaccion) {
}
