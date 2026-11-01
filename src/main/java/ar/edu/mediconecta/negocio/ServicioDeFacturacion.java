package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.modelo.ReclamoObraSocial;
import jakarta.ejb.Local;

import java.util.List;

/** ServicioDeFacturacion: reclamos a obras sociales por prestaciones autorizadas y reintegros por cancelación. */
@Local
public interface ServicioDeFacturacion {

    void procesarEvento(EventoTurno evento);

    /** Todos los reclamos a obras sociales, para el personal administrativo. */
    List<ReclamoObraSocial> todosLosReclamos();
}
