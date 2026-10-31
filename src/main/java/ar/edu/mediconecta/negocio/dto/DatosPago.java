package ar.edu.mediconecta.negocio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;

/**
 * Datos de la tarjeta que ingresa el paciente. Bean Validation se aplica dos veces: en la vista (JSF los
 * valida al enviar el formulario) y en {@code ServicioDePagosBean} antes de llamar a la pasarela. Nunca
 * se persisten: viajan a la pasarela y se descartan.
 */
public class DatosPago implements Serializable {

    @NotBlank(message = "ingresá el nombre del titular")
    @Size(max = 80, message = "admite hasta 80 caracteres")
    private String titular;

    @NotBlank(message = "ingresá el número de la tarjeta")
    @Pattern(regexp = "[0-9 ]{13,23}", message = "debe tener entre 13 y 19 dígitos")
    private String numeroTarjeta;

    @NotBlank(message = "ingresá el vencimiento")
    @Pattern(regexp = "(0[1-9]|1[0-2])/[0-9]{2}", message = "usá el formato MM/AA")
    private String vencimiento;

    @NotBlank(message = "ingresá el código de seguridad")
    @Pattern(regexp = "[0-9]{3,4}", message = "tiene 3 o 4 dígitos")
    private String codigoSeguridad;

    public String getTitular() {
        return titular;
    }

    public void setTitular(String titular) {
        this.titular = titular;
    }

    public String getNumeroTarjeta() {
        return numeroTarjeta;
    }

    public void setNumeroTarjeta(String numeroTarjeta) {
        this.numeroTarjeta = numeroTarjeta;
    }

    public String getVencimiento() {
        return vencimiento;
    }

    public void setVencimiento(String vencimiento) {
        this.vencimiento = vencimiento;
    }

    public String getCodigoSeguridad() {
        return codigoSeguridad;
    }

    public void setCodigoSeguridad(String codigoSeguridad) {
        this.codigoSeguridad = codigoSeguridad;
    }
}
