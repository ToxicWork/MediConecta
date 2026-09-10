package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.EntradaClinica;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.TipoEntrada;
import jakarta.ejb.Local;

@Local
public interface ServicioDeHistoriaClinica {

    HistoriaClinica obtenerDePaciente(Long pacienteId);

    EntradaClinica agregarEntrada(Long pacienteId, Long profesionalId, Long turnoId, TipoEntrada tipo, String descripcion);
}
