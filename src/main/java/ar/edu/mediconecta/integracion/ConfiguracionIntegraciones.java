package ar.edu.mediconecta.integracion;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Direcciones y credenciales de los sistemas externos. Se leen de propiedades del sistema para poder
 * apuntar a otro entorno sin recompilar, por ejemplo:
 * {@code standalone.sh -c standalone-full.xml -Dmediconecta.externos.url=http://otrohost:8080/externos}.
 * Los valores por defecto son los de los simuladores que se despliegan junto a la aplicación.
 * Las credenciales son de prueba (las asignan los simuladores), nunca datos reales.
 */
@ApplicationScoped
public class ConfiguracionIntegraciones {

    private static String propiedad(String nombre, String porDefecto) {
        String valor = System.getProperty(nombre);
        return valor == null || valor.isBlank() ? porDefecto : valor.trim();
    }

    public String urlExternos() {
        return propiedad("mediconecta.externos.url", "http://localhost:8080/externos");
    }

    public String urlObraSocial() {
        return propiedad("mediconecta.obrasocial.url", urlExternos() + "/CoberturaService");
    }

    public String usuarioObraSocial() {
        return propiedad("mediconecta.obrasocial.usuario", "mediconecta");
    }

    public String claveObraSocial() {
        return propiedad("mediconecta.obrasocial.clave", "prestador-2026");
    }

    public String urlPasarela() {
        return propiedad("mediconecta.pasarela.url", urlExternos() + "/api/pasarela/v1");
    }

    public String clavePasarela() {
        return propiedad("mediconecta.pasarela.clave", "sk_test_mediconecta_2026");
    }

    public String urlVideoconsulta() {
        return propiedad("mediconecta.video.url", urlExternos() + "/api/video/v1");
    }

    public String claveVideoconsulta() {
        return propiedad("mediconecta.video.clave", "vk_test_mediconecta_2026");
    }

    public String urlWsdlObraSocial() {
        return urlObraSocial() + "?wsdl";
    }
}
