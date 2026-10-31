package ar.edu.mediconecta.modelo;

import java.io.Serializable;
import java.math.BigDecimal;

/** Prestación del nomenclador (código que entiende la obra social) con su precio de lista. */
public class Prestacion implements Serializable {

    private final String codigo;
    private final String descripcion;
    private final BigDecimal precio;

    public Prestacion(String codigo, String descripcion, BigDecimal precio) {
        this.codigo = codigo;
        this.descripcion = descripcion;
        this.precio = precio;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPrecio() {
        return precio;
    }
}
