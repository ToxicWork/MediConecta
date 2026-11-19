package ar.edu.mediconecta.seguridad;

/** Para que las pruebas de otros paquetes fijen la clave del cifrado de columnas (o la saquen). */
public final class ClaveDeCifradoParaPruebas {

    private ClaveDeCifradoParaPruebas() {
    }

    public static void usar(CifradorDeColumnas cifrador) {
        ClaveDeCifrado.usar(cifrador);
    }
}
