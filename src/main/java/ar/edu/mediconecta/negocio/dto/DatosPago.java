package ar.edu.mediconecta.negocio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Datos de la tarjeta que ingresa el paciente. Nunca se persisten: viajan a la pasarela y se descartan.
 *
 * <p>Las reglas son una sola, en las constantes de formato: las usan las anotaciones de Bean Validation (que
 * {@code ServicioDePagosBean} vuelve a verificar antes de llamar a la pasarela) y {@link #mensajeDeError()}, que arma
 * para la pantalla un único mensaje con todo lo que hay que corregir (D-19). Si un campo falta, no se informa además
 * que tiene mal el formato.
 */
public class DatosPago implements Serializable {

    /** Entre 13 y 19 dígitos, con espacios opcionales entre ellos ("4111 1111 1111 1111"). */
    public static final String FORMATO_NUMERO = "(?:[0-9] ?){12,18}[0-9]";
    public static final String FORMATO_VENCIMIENTO = "(0[1-9]|1[0-2])/[0-9]{2}";
    public static final String FORMATO_CODIGO = "[0-9]{3,4}";
    public static final int LARGO_MAXIMO_TITULAR = 80;

    static final String FALTA_TITULAR = "falta el nombre del titular";
    static final String TITULAR_LARGO = "el nombre del titular admite hasta 80 caracteres";
    static final String FALTA_NUMERO = "falta el número de la tarjeta";
    static final String NUMERO_INVALIDO = "el número de la tarjeta tiene entre 13 y 19 dígitos";
    static final String FALTA_VENCIMIENTO = "falta el vencimiento";
    static final String VENCIMIENTO_INVALIDO = "el vencimiento va en formato MM/AA";
    static final String FALTA_CODIGO = "falta el código de seguridad";
    static final String CODIGO_INVALIDO = "el código de seguridad tiene 3 o 4 dígitos";

    @NotBlank(message = FALTA_TITULAR)
    @Size(max = LARGO_MAXIMO_TITULAR, message = TITULAR_LARGO)
    private String titular;

    @NotBlank(message = FALTA_NUMERO)
    @Pattern(regexp = FORMATO_NUMERO, message = NUMERO_INVALIDO)
    private String numeroTarjeta;

    @NotBlank(message = FALTA_VENCIMIENTO)
    @Pattern(regexp = FORMATO_VENCIMIENTO, message = VENCIMIENTO_INVALIDO)
    private String vencimiento;

    @NotBlank(message = FALTA_CODIGO)
    @Pattern(regexp = FORMATO_CODIGO, message = CODIGO_INVALIDO)
    private String codigoSeguridad;

    /**
     * Todo lo que hay que corregir, en un solo mensaje bien escrito (D-19). Primero lo que falta, después lo que tiene
     * mal el formato: "Revisá los datos de la tarjeta: faltan el titular y el código de seguridad; el vencimiento va
     * en formato MM/AA." Vacío si los datos están completos y bien formados.
     */
    public Optional<String> mensajeDeError() {
        List<Problema> problemas = problemas();
        if (problemas.isEmpty()) {
            return Optional.empty();
        }
        List<String> faltan = problemas.stream().filter(Problema::falta).map(Problema::texto).toList();
        List<String> mal = problemas.stream().filter(problema -> !problema.falta()).map(Problema::texto).toList();
        List<String> partes = new ArrayList<>();
        if (!faltan.isEmpty()) {
            partes.add((faltan.size() == 1 ? "falta " : "faltan ") + enumerar(faltan));
        }
        if (!mal.isEmpty()) {
            partes.add(enumerar(mal));
        }
        return Optional.of("Revisá los datos de la tarjeta: " + String.join("; ", partes) + ".");
    }

    /** Nombres de los campos con algún problema (la vista los marca en rojo). */
    public Set<String> camposConError() {
        Set<String> campos = new LinkedHashSet<>();
        problemas().forEach(problema -> campos.add(problema.campo()));
        return campos;
    }

    /** Últimos cuatro dígitos de la tarjeta, para el diálogo de confirmación y el mensaje final. */
    public String getUltimosDigitos() {
        String digitos = numeroTarjeta == null ? "" : numeroTarjeta.replaceAll("\\D", "");
        return digitos.length() >= 4 ? digitos.substring(digitos.length() - 4) : "";
    }

    /** Un problema de un campo: o falta (el texto nombra el dato) o tiene mal el formato (el texto explica la regla). */
    private record Problema(String campo, boolean falta, String texto) {
    }

    private List<Problema> problemas() {
        List<Problema> problemas = new ArrayList<>();
        if (titular == null || titular.isBlank()) {
            problemas.add(new Problema("titular", true, "el nombre del titular"));
        } else if (titular.length() > LARGO_MAXIMO_TITULAR) {
            problemas.add(new Problema("titular", false, TITULAR_LARGO));
        }
        revisar(problemas, "numeroTarjeta", numeroTarjeta, FORMATO_NUMERO, "el número de la tarjeta", NUMERO_INVALIDO);
        revisar(problemas, "vencimiento", vencimiento, FORMATO_VENCIMIENTO, "el vencimiento", VENCIMIENTO_INVALIDO);
        revisar(problemas, "codigoSeguridad", codigoSeguridad, FORMATO_CODIGO, "el código de seguridad", CODIGO_INVALIDO);
        return problemas;
    }

    private static void revisar(List<Problema> problemas, String campo, String valor, String formato,
                                String dato, String siEstaMal) {
        if (valor == null || valor.isBlank()) {
            problemas.add(new Problema(campo, true, dato));
        } else if (!valor.trim().matches(formato)) {
            problemas.add(new Problema(campo, false, siEstaMal));
        }
    }

    /** "a", "a y b", "a, b y c". */
    private static String enumerar(List<String> textos) {
        if (textos.size() == 1) {
            return textos.get(0);
        }
        return String.join(", ", textos.subList(0, textos.size() - 1)) + " y " + textos.get(textos.size() - 1);
    }

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
