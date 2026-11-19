package ar.edu.mediconecta.it;

import ar.edu.mediconecta.negocio.ServicioDePagos;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/** Facturación reintegra el pago de un turno cancelado, con la identidad del sistema. */
@Stateless
@PermitAll
@RunAs("SISTEMA")
@DeclareRoles("SISTEMA")
public class SistemaDePrueba {

    @Inject
    private ServicioDePagos pagos;

    public void reintegrarPagoDeTurno(Long turnoId) {
        pagos.reembolsarPagoDeTurno(turnoId, "Cancelación del turno por el paciente");
    }
}
