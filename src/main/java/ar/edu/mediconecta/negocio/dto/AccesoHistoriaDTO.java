package ar.edu.mediconecta.negocio.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Un acceso de un tercero a la historia clínica del paciente (lo registra el interceptor de auditoría). */
public class AccesoHistoriaDTO implements Serializable {

    private final LocalDateTime fecha;
    private final String quien;
    private final String operacion;
    private final String resultado;

    public AccesoHistoriaDTO(LocalDateTime fecha, String quien, String operacion, String resultado) {
        this.fecha = fecha;
        this.quien = quien;
        this.operacion = operacion;
        this.resultado = resultado;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public String getQuien() {
        return quien;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getResultado() {
        return resultado;
    }

    public boolean isPermitido() {
        return "PERMITIDO".equals(resultado);
    }
}
