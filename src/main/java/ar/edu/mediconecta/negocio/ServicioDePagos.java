package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.modelo.Pago;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import jakarta.ejb.Local;

import java.math.BigDecimal;
import java.util.List;

/** ServicioDePagos: cobros de turnos particulares y copagos, a través de la pasarela externa (REST). */
@Local
public interface ServicioDePagos {

    Pago cobrar(Turno turno, BigDecimal importe, DatosPago datosPago);

    void reembolsarPagoDeTurno(Long turnoId, String motivo);

    List<Pago> pagosDelUsuarioActual();
}
