package ar.edu.mediconecta.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Traza de acceso a datos sensibles (patrón Intercepting Filter, {@code AuditoriaInterceptor}):
 * quién, qué operación, sobre qué recurso y con qué resultado. Guarda identificadores, nunca el dato clínico.
 */
@Entity
@Table(name = "registro_acceso")
public class RegistroAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario", nullable = false, length = 120)
    private String usuario;

    @Column(name = "operacion", nullable = false, length = 80)
    private String operacion;

    @Column(name = "recurso", nullable = false, length = 60)
    private String recurso;

    @Column(name = "recurso_id")
    private Long recursoId;

    @Column(name = "resultado", nullable = false, length = 20)
    private String resultado;

    @Column(name = "detalle", length = 300)
    private String detalle;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected RegistroAcceso() {
    }

    public RegistroAcceso(String usuario, String operacion, String recurso, Long recursoId, String resultado, String detalle) {
        this.usuario = usuario;
        this.operacion = operacion;
        this.recurso = recurso;
        this.recursoId = recursoId;
        this.resultado = resultado;
        this.detalle = detalle;
        this.fecha = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getOperacion() {
        return operacion;
    }

    public String getRecurso() {
        return recurso;
    }

    public Long getRecursoId() {
        return recursoId;
    }

    public String getResultado() {
        return resultado;
    }

    public String getDetalle() {
        return detalle;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
