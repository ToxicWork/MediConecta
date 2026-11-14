package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.modelo.LlamadaIntegracion;
import ar.edu.mediconecta.negocio.dto.FilaAutorizacionDTO;
import ar.edu.mediconecta.negocio.dto.FilaMonitorDTO;
import jakarta.ejb.Local;

import java.util.List;

@Local
public interface ServicioDeMonitoreo {

    /**
     * Una página de los eventos de los turnos del usuario autenticado (del más nuevo al más viejo), con el estado
     * de cada suscriptor. Cada usuario ve sólo los de sus turnos.
     */
    List<FilaMonitorDTO> eventosDelUsuarioActual(int desde, int cantidad);

    /** Cuántos eventos tienen los turnos del usuario autenticado (para el paginador). */
    int contarEventosDelUsuarioActual();

    /** Solicitudes recientes de la cola de autorizaciones para los turnos del usuario autenticado. */
    List<FilaAutorizacionDTO> autorizacionesRecientesDelUsuarioActual();

    /** Estado de los Circuit Breakers de los tres sistemas externos. */
    List<EstadoCircuito> circuitos();

    /** Últimas llamadas a sistemas externos (SOAP y REST). */
    List<LlamadaIntegracion> llamadasRecientes();
}
