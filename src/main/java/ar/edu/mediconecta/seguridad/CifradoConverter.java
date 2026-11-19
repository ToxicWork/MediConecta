package ar.edu.mediconecta.seguridad;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra una columna con AES-GCM al guardarla y la descifra al leerla (diseño, sección 8.6; ADR-0022). Se aplica con
 * {@code @Convert(converter = CifradoConverter.class)} sobre el atributo: hoy, el n.º de afiliado del paciente y la
 * descripción de las entradas de la historia clínica (diagnósticos y recetas). El resto de la aplicación ve el texto
 * en claro; la base, sólo {@code enc:v1:...}.
 */
@Converter
public class CifradoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String valor) {
        return valor == null ? null : ClaveDeCifrado.cifrador().cifrar(valor);
    }

    @Override
    public String convertToEntityAttribute(String valor) {
        return valor == null ? null : ClaveDeCifrado.cifrador().descifrar(valor);
    }
}
