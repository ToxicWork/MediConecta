package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.ejb.Local;

import java.util.List;
import java.util.Optional;

/**
 * Consultas de agenda sin estado conversacional, para cualquier capa de presentación (la API REST). Se
 * separa de {@code ServicioDeTurnos}, que es {@code @Stateful} y modela la conversación del hold.
 */
@Local
public interface ServicioDeAgenda {

    List<Profesional> profesionales();

    List<Turno> disponibilidadDe(Long profesionalId);

    List<Turno> turnosDelUsuarioActual();

    Optional<Turno> turnoDelUsuarioActual(Long turnoId);
}
