package ar.edu.mediconecta.negocio.copago;

import java.io.Serializable;
import java.math.BigDecimal;

/** Resultado de una estrategia de copago: cuánto paga el paciente y cuánto la obra social. */
public class ImporteTurno implements Serializable {

    private final BigDecimal precioLista;
    private final BigDecimal importePaciente;
    private final BigDecimal importeObraSocial;
    private final String regla;

    public ImporteTurno(BigDecimal precioLista, BigDecimal importePaciente, BigDecimal importeObraSocial, String regla) {
        this.precioLista = precioLista;
        this.importePaciente = importePaciente;
        this.importeObraSocial = importeObraSocial;
        this.regla = regla;
    }

    public boolean isRequierePago() {
        return importePaciente.signum() > 0;
    }

    public BigDecimal getPrecioLista() {
        return precioLista;
    }

    public BigDecimal getImportePaciente() {
        return importePaciente;
    }

    public BigDecimal getImporteObraSocial() {
        return importeObraSocial;
    }

    public String getRegla() {
        return regla;
    }
}
