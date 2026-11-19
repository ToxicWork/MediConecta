package ar.edu.mediconecta.seguridad;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifrado en reposo de las columnas sensibles (documento de diseño de patrones y seguridad, sección 8.6; ADR-0022):
 * AES-256 en modo GCM, que además de ocultar el dato detecta si alguien lo alteró en la base.
 *
 * <p>Cada valor se cifra con un vector de inicialización (IV) de 12 bytes al azar, así que el mismo texto da un
 * resultado distinto cada vez y no se puede saber, mirando la base, si dos pacientes tienen el mismo dato. En la
 * columna se guarda {@code enc:v1:} seguido del IV, el texto cifrado y la etiqueta de autenticación de 16 bytes, en
 * Base64. El {@code v1} deja lugar para rotar la clave más adelante sin perder lo ya cifrado.
 *
 * <p>Un valor sin ese prefijo es de antes del cifrado: se devuelve tal cual, y {@code CifradoDeDatosExistentes} lo
 * cifra al desplegar. La clave (32 bytes en Base64) la entrega el servidor; ver {@link ClaveDeCifrado}.
 */
public final class CifradorDeColumnas {

    public static final String PREFIJO = "enc:v1:";

    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int BYTES_IV = 12;
    private static final int BITS_ETIQUETA = 128;
    private static final int BYTES_CLAVE = 32;
    /** Uno solo, reutilizado (Sonar S2119). */
    private static final SecureRandom AZAR = new SecureRandom();

    private final SecretKeySpec clave;

    public CifradorDeColumnas(byte[] clave) {
        if (clave == null || clave.length != BYTES_CLAVE) {
            throw new IllegalArgumentException("La clave de cifrado de columnas tiene que ser de 32 bytes (AES-256).");
        }
        this.clave = new SecretKeySpec(clave.clone(), "AES");
    }

    /** La clave en Base64, como la guarda la configuración del servidor. */
    public static CifradorDeColumnas deBase64(String claveEnBase64) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(claveEnBase64.trim());
        } catch (IllegalArgumentException noEsBase64) {
            throw new IllegalArgumentException("La clave de cifrado de columnas no es Base64 válido.", noEsBase64);
        }
        return new CifradorDeColumnas(bytes);
    }

    public static boolean estaCifrado(String valor) {
        return valor != null && valor.startsWith(PREFIJO);
    }

    public String cifrar(String texto) {
        if (texto == null) {
            return null;
        }
        byte[] iv = new byte[BYTES_IV];
        AZAR.nextBytes(iv);
        try {
            Cipher cifra = Cipher.getInstance(TRANSFORMACION);
            cifra.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, iv));
            byte[] cifrado = cifra.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            byte[] todo = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
            return PREFIJO + Base64.getEncoder().encodeToString(todo);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el dato.", e);
        }
    }

    public String descifrar(String guardado) {
        if (!estaCifrado(guardado)) {
            return guardado; // nulo o de antes del cifrado
        }
        byte[] todo;
        try {
            todo = Base64.getDecoder().decode(guardado.substring(PREFIJO.length()));
        } catch (IllegalArgumentException e) {
            throw new DatoCifradoInvalidoException("El dato cifrado no es Base64 válido.", e);
        }
        if (todo.length < BYTES_IV + BITS_ETIQUETA / 8) {
            throw new DatoCifradoInvalidoException("El dato cifrado está incompleto.", null);
        }
        try {
            Cipher cifra = Cipher.getInstance(TRANSFORMACION);
            cifra.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, todo, 0, BYTES_IV));
            return new String(cifra.doFinal(todo, BYTES_IV, todo.length - BYTES_IV), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new DatoCifradoInvalidoException(
                    "El dato cifrado fue alterado o se cifró con otra clave (falla la autenticación de AES-GCM).", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo descifrar el dato.", e);
        }
    }

    /** El dato de la base no se puede descifrar: fue modificado por fuera de la aplicación o la clave no es la suya. */
    public static class DatoCifradoInvalidoException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public DatoCifradoInvalidoException(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }
}
