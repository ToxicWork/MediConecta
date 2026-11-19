package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.negocio.ServicioDePacientes;
import ar.edu.mediconecta.negocio.dto.FichaPaciente;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;

/**
 * El diálogo "Ficha del paciente" de Usuarios (administrativo) y de la Agenda (profesional). Pide la ficha al
 * servicio, que llega ya decorada por {@code PacienteEnmascarado}: esta pantalla no decide qué se ve, sólo muestra lo
 * que viene, y avisa cuando los datos clínicos se ocultaron.
 */
@Named
@ViewScoped
public class FichaPacienteBean implements Serializable {

    private static final long serialVersionUID = 1L;

    @Inject
    private ServicioDePacientes servicioDePacientes;

    private FichaPaciente ficha;

    public void abrir(Long pacienteId) {
        try {
            ficha = servicioDePacientes.ficha(pacienteId);
        } catch (ErrorDeNegocioException e) {
            ficha = null;
            Mensajes.error(e.getMessage());
        }
    }

    public FichaPaciente getFicha() {
        return ficha;
    }

    public String tipo(String tipo) {
        return "RECETA".equals(tipo) ? "Receta" : "Diagnóstico";
    }
}
