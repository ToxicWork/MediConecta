package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.negocio.dto.FilaMonitorDTO;
import jakarta.ejb.Local;

import java.util.List;

@Local
public interface ServicioDeMonitoreo {

    /** Eventos recientes de los turnos del usuario autenticado, con el estado de cada suscriptor. */
    List<FilaMonitorDTO> eventosRecientesDelUsuarioActual();
}
