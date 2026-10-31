package ar.externos.obrasocial;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

@XmlType(name = "EstadoAutorizacion")
@XmlEnum
public enum EstadoAutorizacion {
    AUTORIZADA,
    RECHAZADA
}
