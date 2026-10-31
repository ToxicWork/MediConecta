package ar.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "EstadoCobertura")
@XmlEnum
public enum EstadoCobertura {
    CUBIERTO,
    NO_CUBIERTO,
    AFILIADO_SUSPENDIDO,
    AFILIADO_INEXISTENTE
}
