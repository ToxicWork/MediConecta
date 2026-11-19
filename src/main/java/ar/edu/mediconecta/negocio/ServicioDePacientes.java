package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.negocio.dto.FichaPaciente;
import jakarta.ejb.Local;

/**
 * La ficha del paciente (documento de diseño, sección 4.8: el componente del patrón Decorator). La implementación
 * ({@link ServicioDePacientesBean}) arma la ficha completa; el decorador {@link PacienteEnmascarado} le quita los
 * datos clínicos cuando quien la pide no es el profesional que atiende al paciente.
 */
@Local
public interface ServicioDePacientes {

    /** Cuántas entradas de la historia clínica trae la ficha, de la más nueva a la más vieja. */
    int ENTRADAS_EN_LA_FICHA = 3;

    FichaPaciente ficha(Long pacienteId);
}
