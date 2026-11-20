package ar.edu.mediconecta.it;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * El lado cliente de {@link ApiRestAssuredIT}: REST Assured contra la API desplegada, como la usaría un sistema de
 * afuera (HTTPS, JSON y el JWT en {@code Authorization}). Está aparte y devuelve sólo tipos del JDK
 * ({@link RespuestaApi}): REST Assured existe del lado del cliente, y la clase de la prueba también se carga adentro
 * del servidor.
 */
final class ClienteApi {

    static final String BASE = System.getProperty("mediconecta.api", "https://localhost:8443/mediconecta/api/v1");

    static {
        RestAssured.useRelaxedHTTPSValidation(); // el certificado autofirmado de WildFly en localhost
    }

    private ClienteApi() {
    }

    /**
     * Un pedido a la API. {@code cuerpo} puede ser un Map (va como JSON), un String (va tal cual, con {@code tipo}) o
     * null; {@code cabeceras} son las extra (Authorization, If-Match, Accept...).
     */
    static RespuestaApi pedir(String metodo, String ruta, Object cuerpo, String tipo, Map<String, String> cabeceras) {
        RequestSpecification pedido = given().baseUri(BASE).header("Accept", "application/json");
        if (cabeceras != null) {
            cabeceras.forEach(pedido::header);
        }
        if (cuerpo != null) {
            // El JSON se arma acá (los cuerpos son pares texto-texto): así REST Assured no necesita Jackson ni Gson.
            pedido.contentType(tipo == null ? "application/json" : tipo).body(cuerpo instanceof Map<?, ?> m ? json(m) : cuerpo.toString());
        } else if ("POST".equals(metodo)) {
            pedido.contentType("application/json"); // sin esto REST Assured declara texto y la API responde 415
        }
        Response respuesta = pedido.request(metodo, ruta);
        Map<String, String> recibidas = new java.util.HashMap<>();
        respuesta.getHeaders().forEach(h -> recibidas.put(h.getName().toLowerCase(), h.getValue()));
        return new RespuestaApi(respuesta.getStatusCode(), respuesta.getBody().asString(), recibidas);
    }

    static String json(Map<?, ?> campos) {
        StringBuilder texto = new StringBuilder("{");
        campos.forEach((clave, valor) -> texto.append(texto.length() > 1 ? "," : "")
                .append('"').append(escapar(clave)).append("\":\"").append(escapar(valor)).append('"'));
        return texto.append('}').toString();
    }

    private static String escapar(Object valor) {
        return String.valueOf(valor).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static Map<String, String> bearer(String token) {
        return Map.of("Authorization", "Bearer " + token);
    }

    static String token(String email, String clave) {
        RespuestaApi login = pedir("POST", "/auth/login", Map.of("email", email, "password", clave), null, null);
        if (login.estado() != 200) {
            throw new AssertionError("no se pudo ingresar con " + email + ": " + login.estado() + " " + login.cuerpo());
        }
        return JsonPath.from(login.cuerpo()).getString("token");
    }

    static String campo(String json, String camino) {
        Object valor = JsonPath.from(json).get(camino);
        return valor == null ? null : valor.toString();
    }

    /** El id del turno confirmado más nuevo del paciente (el que preparó la prueba en el servidor). */
    static long turnoConfirmado(String token) {
        List<Map<String, Object>> turnos = JsonPath.from(pedir("GET", "/turnos", null, null, bearer(token)).cuerpo()).getList("$");
        return turnos.stream().filter(t -> "CONFIRMADO".equals(t.get("estado")))
                .map(t -> ((Number) t.get("id")).longValue()).max(Long::compare)
                .orElseThrow(() -> new AssertionError("el paciente de la prueba no tiene un turno confirmado"));
    }
}
