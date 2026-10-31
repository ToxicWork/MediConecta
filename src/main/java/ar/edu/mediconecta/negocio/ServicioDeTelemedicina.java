package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.Turno;
import jakarta.ejb.Local;

import java.util.List;

/** ServicioDeTelemedicina: salas de videoconsulta, creadas en el proveedor externo (REST). */
@Local
public interface ServicioDeTelemedicina {

    List<Turno> turnosVirtualesDelUsuarioActual();

    Turno iniciarVideoconsulta(Long turnoId);
}
