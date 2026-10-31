package ar.edu.mediconecta.negocio.dto;

import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.modelo.Prestacion;
import ar.edu.mediconecta.negocio.copago.ImporteTurno;

import java.io.Serializable;

/** Lo que el paciente ve antes de confirmar: prestación, respuesta de la obra social (SOAP) e importes. */
public class CotizacionTurnoDTO implements Serializable {

    private final Prestacion prestacion;
    private final ResultadoCobertura cobertura;
    private final ImporteTurno importe;

    public CotizacionTurnoDTO(Prestacion prestacion, ResultadoCobertura cobertura, ImporteTurno importe) {
        this.prestacion = prestacion;
        this.cobertura = cobertura;
        this.importe = importe;
    }

    public Prestacion getPrestacion() {
        return prestacion;
    }

    public ResultadoCobertura getCobertura() {
        return cobertura;
    }

    public ImporteTurno getImporte() {
        return importe;
    }

    public boolean isRequierePago() {
        return importe.isRequierePago();
    }
}
