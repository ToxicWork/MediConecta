package ar.edu.mediconecta.negocio.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * La ficha del paciente que consultan profesionales y administrativos (documento de diseño, sección 4.8): los datos
 * de identificación y de cobertura, que necesitan los dos, y el resumen clínico (las últimas entradas de la historia),
 * que sólo puede ver el profesional que lo atiende. Lo decide el decorador {@code PacienteEnmascarado}, que le
 * entrega a los demás {@link #sinDatosClinicos()}.
 */
public class FichaPaciente implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long pacienteId;
    private final String nombreCompleto;
    private final String email;
    private final String obraSocial;
    private final String nroAfiliado;
    private final List<EntradaDeFicha> ultimasEntradas;
    private final int totalDeEntradas;
    private final boolean datosClinicosVisibles;

    public FichaPaciente(Long pacienteId, String nombreCompleto, String email, String obraSocial, String nroAfiliado,
                         List<EntradaDeFicha> ultimasEntradas, int totalDeEntradas) {
        this(pacienteId, nombreCompleto, email, obraSocial, nroAfiliado, ultimasEntradas, totalDeEntradas, true);
    }

    private FichaPaciente(Long pacienteId, String nombreCompleto, String email, String obraSocial, String nroAfiliado,
                          List<EntradaDeFicha> ultimasEntradas, int totalDeEntradas, boolean datosClinicosVisibles) {
        this.pacienteId = pacienteId;
        this.nombreCompleto = nombreCompleto;
        this.email = email;
        this.obraSocial = obraSocial;
        this.nroAfiliado = nroAfiliado;
        this.ultimasEntradas = List.copyOf(ultimasEntradas);
        this.totalDeEntradas = totalDeEntradas;
        this.datosClinicosVisibles = datosClinicosVisibles;
    }

    /** La misma ficha sin nada clínico: ni las entradas ni cuántas hay. */
    public FichaPaciente sinDatosClinicos() {
        return new FichaPaciente(pacienteId, nombreCompleto, email, obraSocial, nroAfiliado, List.of(), 0, false);
    }

    public Long getPacienteId() {
        return pacienteId;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getEmail() {
        return email;
    }

    public String getObraSocial() {
        return obraSocial;
    }

    public String getNroAfiliado() {
        return nroAfiliado;
    }

    public List<EntradaDeFicha> getUltimasEntradas() {
        return ultimasEntradas;
    }

    public int getTotalDeEntradas() {
        return totalDeEntradas;
    }

    public boolean isDatosClinicosVisibles() {
        return datosClinicosVisibles;
    }

    /** Una entrada de la historia clínica, resumida para la ficha. */
    public static class EntradaDeFicha implements Serializable {

        private static final long serialVersionUID = 1L;

        private final LocalDateTime fecha;
        private final String tipo;
        private final String profesional;
        private final String descripcion;

        public EntradaDeFicha(LocalDateTime fecha, String tipo, String profesional, String descripcion) {
            this.fecha = fecha;
            this.tipo = tipo;
            this.profesional = profesional;
            this.descripcion = descripcion;
        }

        public LocalDateTime getFecha() {
            return fecha;
        }

        public String getTipo() {
            return tipo;
        }

        public String getProfesional() {
            return profesional;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }
}
