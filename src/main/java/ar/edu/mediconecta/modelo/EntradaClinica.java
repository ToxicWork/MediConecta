package ar.edu.mediconecta.modelo;

import ar.edu.mediconecta.seguridad.CifradoConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "entrada_clinica")
public class EntradaClinica implements Serializable {

    public static final int LARGO_MAXIMO = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "historia_clinica_id", nullable = false)
    private HistoriaClinica historiaClinica;

    @ManyToOne(optional = false)
    @JoinColumn(name = "profesional_id", nullable = false)
    private Profesional profesional;

    @ManyToOne
    @JoinColumn(name = "turno_id")
    private Turno turno;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoEntrada tipo;

    /**
     * El diagnóstico o la receta: hasta {@value #LARGO_MAXIMO} caracteres. En la base va cifrado con AES-GCM
     * (ADR-0022), por eso la columna es más ancha que el texto.
     */
    @Convert(converter = CifradoConverter.class)
    @Column(name = "descripcion", nullable = false, length = 8100)
    private String descripcion;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected EntradaClinica() {
    }

    public EntradaClinica(Profesional profesional, Turno turno, TipoEntrada tipo, String descripcion) {
        this.profesional = profesional;
        this.turno = turno;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.fecha = LocalDateTime.now();
    }

    void asociarA(HistoriaClinica historiaClinica) {
        this.historiaClinica = historiaClinica;
    }

    public Long getId() {
        return id;
    }

    public Profesional getProfesional() {
        return profesional;
    }

    public Turno getTurno() {
        return turno;
    }

    public TipoEntrada getTipo() {
        return tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
