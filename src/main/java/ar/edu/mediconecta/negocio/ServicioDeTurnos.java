package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.dto.CotizacionTurnoDTO;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import jakarta.ejb.Local;

import java.time.LocalDateTime;
import java.util.List;

@Local
public interface ServicioDeTurnos {

    Turno publicarDisponibilidad(Long profesionalId, LocalDateTime fechaHora, Modalidad modalidad);

    List<Turno> buscarDisponibilidad(Long profesionalId);

    Turno reservarHold(Long turnoId, Long pacienteId);

    /** Consulta la cobertura (SOAP) y calcula el copago del turno en espera, sin efectos. */
    CotizacionTurnoDTO cotizarTurnoEnHold();

    /** Flujo crítico: valida, consulta cobertura, cobra, confirma, encola la autorización y publica el evento. */
    Turno confirmar(DatosPago datosPago);

    void liberarHold();

    Turno getTurnoEnHold();

    List<Turno> buscarConfirmadosDePaciente(Long pacienteId);

    List<Turno> buscarConfirmadosDeProfesional(Long profesionalId);

    Turno cancelar(Long turnoId, Long pacienteId);
}
