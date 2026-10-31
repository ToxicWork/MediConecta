package ar.externos.videoconsulta;

import java.time.LocalDateTime;

/** Sala de videoconsulta del proveedor externo (simulado). */
public class Sala {

    public enum Estado { ABIERTA, CERRADA }

    private String id;
    private Estado estado;
    private String referencia;
    private String titulo;
    private String profesional;
    private String paciente;
    private String urlProfesional;
    private String urlPaciente;
    private LocalDateTime creadaEn;
    private LocalDateTime expiraEn;

    public Sala() {
    }

    Sala(String id, String referencia, String titulo, String profesional, String paciente, String base) {
        this.id = id;
        this.estado = Estado.ABIERTA;
        this.referencia = referencia;
        this.titulo = titulo;
        this.profesional = profesional;
        this.paciente = paciente;
        this.urlProfesional = base + "/sala.html?id=" + id + "&rol=profesional";
        this.urlPaciente = base + "/sala.html?id=" + id + "&rol=paciente";
        this.creadaEn = LocalDateTime.now();
        this.expiraEn = creadaEn.plusHours(2);
    }

    void cerrar() {
        this.estado = Estado.CERRADA;
    }

    public String getId() {
        return id;
    }

    public Estado getEstado() {
        return estado;
    }

    public String getReferencia() {
        return referencia;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getProfesional() {
        return profesional;
    }

    public String getPaciente() {
        return paciente;
    }

    public String getUrlProfesional() {
        return urlProfesional;
    }

    public String getUrlPaciente() {
        return urlPaciente;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }
}
