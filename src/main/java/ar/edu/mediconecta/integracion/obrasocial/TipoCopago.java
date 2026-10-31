package ar.edu.mediconecta.integracion.obrasocial;

/** Cómo calcula el copago la obra social (lo informa el legado en validarCobertura). */
public enum TipoCopago {
    PORCENTAJE,
    MONTO_FIJO,
    EXENTO,
    NINGUNO
}
