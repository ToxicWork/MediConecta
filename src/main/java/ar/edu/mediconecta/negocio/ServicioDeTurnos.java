package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.Turno;
import jakarta.ejb.Local;

import java.time.LocalDateTime;
import java.util.List;

@Local
public interface ServicioDeTurnos {

    Turno publicarDisponibilidad(Long profesionalId, LocalDateTime fechaHora);

    List<Turno> buscarDisponibilidad(Long profesionalId);

    Turno reservarHold(Long turnoId, Long pacienteId);

    Turno confirmar();

    Turno getTurnoEnHold();

    List<Turno> buscarConfirmadosDePaciente(Long pacienteId);

    List<Turno> buscarConfirmadosDeProfesional(Long profesionalId);
}
