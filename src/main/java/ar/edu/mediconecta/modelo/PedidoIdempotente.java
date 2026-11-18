package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

/**
 * Un pedido a la API con {@code Idempotency-Key} y la respuesta que recibió (Eje C). Si el cliente lo repite con la
 * misma clave, recibe exactamente esa respuesta y la operación no se vuelve a hacer. La clave es de cada usuario: dos
 * usuarios pueden usar la misma sin pisarse. Se guarda en la misma transacción que la operación, así nunca queda una
 * respuesta guardada de una operación que no se concretó. Vale 24 horas.
 */
@Entity
@Table(name = "pedido_idempotente",
        uniqueConstraints = @UniqueConstraint(name = "uk_pedido_idempotente_usuario_clave", columnNames = {"usuario_id", "clave"}),
        indexes = @Index(name = "ix_pedido_idempotente_creado_en", columnList = "creado_en"))
public class PedidoIdempotente {

    public static final int LARGO_MAXIMO_CLAVE = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = LARGO_MAXIMO_CLAVE)
    private String clave;

    /** Método y ruta del pedido: la misma clave en otro pedido es un error del cliente (422). */
    @Column(nullable = false, length = 200)
    private String operacion;

    @Column(name = "estado_http", nullable = false)
    private int estadoHttp;

    @Column(nullable = false, columnDefinition = "text")
    private String respuesta;

    @Column(length = 40)
    private String etag;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected PedidoIdempotente() {
        // JPA
    }

    public PedidoIdempotente(Long usuarioId, String clave, String operacion, int estadoHttp, String respuesta,
                             String etag, LocalDateTime creadoEn) {
        this.usuarioId = usuarioId;
        this.clave = clave;
        this.operacion = operacion;
        this.estadoHttp = estadoHttp;
        this.respuesta = respuesta;
        this.etag = etag;
        this.creadoEn = creadoEn;
    }

    public boolean esDeLaOperacion(String otra) {
        return operacion.equals(otra);
    }

    public Long getId() {
        return id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getClave() {
        return clave;
    }

    public String getOperacion() {
        return operacion;
    }

    public int getEstadoHttp() {
        return estadoHttp;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public String getEtag() {
        return etag;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
