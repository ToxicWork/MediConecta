package ar.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/** PORCENTAJE: valorCopago es el % a cargo del afiliado. MONTO_FIJO: importe fijo. EXENTO: sin copago. */
@XmlType(name = "TipoCopago")
@XmlEnum
public enum TipoCopago {
    PORCENTAJE,
    MONTO_FIJO,
    EXENTO,
    NINGUNO
}
