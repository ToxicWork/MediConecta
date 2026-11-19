package ar.edu.mediconecta.seguridad;

import java.util.function.UnaryOperator;

/**
 * De dónde sale la clave del cifrado de columnas (ADR-0022). Nunca está en el repositorio ni en texto plano en la
 * configuración del servidor:
 * <ul>
 *   <li>Lo normal: la propiedad de sistema {@value #PROPIEDAD}, cuyo valor en {@code standalone-full.xml} es una
 *       expresión cifrada de Elytron ({@code ${ENC::mediconecta:...}}). La descifra el servidor al arrancar con la
 *       clave que guarda el credential store {@code mediconecta-cs}. Lo arma {@code wildfly/configurar_cifrado.py}.</li>
 *   <li>Alternativa para una máquina de desarrollo: la variable de entorno {@value #VARIABLE}.</li>
 * </ul>
 * Sin clave la aplicación no despliega (lo verifica {@code CifradoDeDatosExistentes}): a diferencia del secreto del
 * JWT, una clave al azar dejaría ilegibles los datos ya guardados.
 */
public final class ClaveDeCifrado {

    public static final String PROPIEDAD = "mediconecta.cifrado.clave";
    public static final String VARIABLE = "MEDICONECTA_CLAVE_CIFRADO";

    private static volatile CifradorDeColumnas cifrador;

    private ClaveDeCifrado() {
    }

    /** El cifrador con la clave del servidor; la primera vez la lee de la configuración. */
    public static CifradorDeColumnas cifrador() {
        CifradorDeColumnas actual = cifrador;
        if (actual == null) {
            synchronized (ClaveDeCifrado.class) {
                if (cifrador == null) {
                    cifrador = desdeConfiguracion(System::getProperty, System::getenv);
                }
                actual = cifrador;
            }
        }
        return actual;
    }

    static CifradorDeColumnas desdeConfiguracion(UnaryOperator<String> propiedades, UnaryOperator<String> entorno) {
        String clave = propiedades.apply(PROPIEDAD);
        if (clave == null || clave.isBlank()) {
            clave = entorno.apply(VARIABLE);
        }
        if (clave == null || clave.isBlank()) {
            throw new IllegalStateException("Falta la clave del cifrado de columnas (propiedad de sistema " + PROPIEDAD
                    + " o variable de entorno " + VARIABLE + "). Configurala con wildfly/configurar_cifrado.py; ver SETUP.md.");
        }
        if (clave.startsWith("${")) {
            throw new IllegalStateException("La propiedad " + PROPIEDAD + " no se resolvió (" + clave.substring(0, 6)
                    + "...): revisá el credential store mediconecta-cs y el resolver de expresiones de Elytron.");
        }
        return CifradorDeColumnas.deBase64(clave);
    }

    /** Sólo para las pruebas unitarias. */
    static void usar(CifradorDeColumnas paraPruebas) {
        cifrador = paraPruebas;
    }
}
