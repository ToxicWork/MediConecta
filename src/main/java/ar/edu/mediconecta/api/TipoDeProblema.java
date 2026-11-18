package ar.edu.mediconecta.api;

/**
 * Tipos de problema de la API (Problem Details, RFC 9457). Cada uno tiene un código estable (que también va en el
 * campo {@code error}), el código HTTP y un título fijo; su {@code type} es la dirección de su explicación en
 * {@code docs/api/problemas.md}.
 */
public enum TipoDeProblema {

    PEDIDO_INVALIDO("pedido_invalido", 400, "Pedido mal formado"),
    NO_AUTENTICADO("no_autenticado", 401, "Falta autenticarse"),
    TOKEN_INVALIDO("token_invalido", 401, "Token inválido o vencido"),
    CREDENCIALES_INVALIDAS("credenciales_invalidas", 401, "Credenciales inválidas"),
    PROHIBIDO("prohibido", 403, "Operación no permitida"),
    PASSWORD_TEMPORAL("password_temporal", 403, "Contraseña temporal sin cambiar"),
    NO_ENCONTRADO("no_encontrado", 404, "Recurso inexistente"),
    METODO_NO_PERMITIDO("metodo_no_permitido", 405, "Método no permitido"),
    NO_ACEPTABLE("no_aceptable", 406, "Formato de respuesta no disponible"),
    TRANSICION_INVALIDA("transicion_invalida", 409, "Transición de estado inválida"),
    REGLA_DE_NEGOCIO("regla_de_negocio", 409, "Regla de negocio"),
    PRECONDICION_FALLIDA("precondicion_fallida", 412, "El recurso cambió"),
    TIPO_NO_SOPORTADO("tipo_no_soportado", 415, "Tipo de contenido no soportado"),
    DATOS_INVALIDOS("datos_invalidos", 422, "Datos inválidos"),
    CLAVE_REUTILIZADA("clave_reutilizada", 422, "Idempotency-Key ya usada en otro pedido"),
    DEMASIADOS_INTENTOS("demasiados_intentos", 429, "Demasiados intentos"),
    ERROR_INTERNO("error_interno", 500, "Error interno"),
    /** Cualquier otro código HTTP que devuelva el propio JAX-RS: el estado y el título salen del código. */
    ERROR_HTTP("error_http", 400, "Error en el pedido");

    static final String DOCUMENTACION = "https://github.com/ToxicWork/MediConecta/blob/main/docs/api/problemas.md#";

    private final String codigo;
    private final int estado;
    private final String titulo;

    TipoDeProblema(String codigo, int estado, String titulo) {
        this.codigo = codigo;
        this.estado = estado;
        this.titulo = titulo;
    }

    public String codigo() {
        return codigo;
    }

    public int estado() {
        return estado;
    }

    public String titulo() {
        return titulo;
    }

    /** El {@code type} del problema: dónde se explica qué significa y qué hacer. */
    public String uri() {
        return DOCUMENTACION + codigo;
    }
}
