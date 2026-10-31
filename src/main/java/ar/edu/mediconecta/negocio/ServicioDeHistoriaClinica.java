package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.EntradaClinica;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.TipoEntrada;
import ar.edu.mediconecta.negocio.dto.AccesoHistoriaDTO;
import jakarta.ejb.Local;

import java.util.List;

@Local
public interface ServicioDeHistoriaClinica {

    HistoriaClinica obtenerDePaciente(Long pacienteId);

    /** El profesional es siempre el usuario autenticado: no se acepta como parámetro. */
    EntradaClinica agregarEntrada(Long pacienteId, Long turnoId, TipoEntrada tipo, String descripcion);

    /** Accesos de profesionales a la historia del paciente autenticado (trazabilidad, Ley 26.529). */
    List<AccesoHistoriaDTO> accesosAMiHistoria();
}
