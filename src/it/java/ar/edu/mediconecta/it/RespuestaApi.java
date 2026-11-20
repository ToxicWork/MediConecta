package ar.edu.mediconecta.it;

import java.util.Map;

/** Lo que respondió la API: el código, el cuerpo y las cabeceras (con el nombre en minúsculas). */
record RespuestaApi(int estado, String cuerpo, Map<String, String> cabeceras) {

    String cabecera(String nombre) {
        return cabeceras.get(nombre.toLowerCase());
    }
}
