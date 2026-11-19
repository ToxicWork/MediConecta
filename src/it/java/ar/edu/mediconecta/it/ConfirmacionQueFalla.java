package ar.edu.mediconecta.it;

import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.ProcesoConfirmacionTurno;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RunAs;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

/**
 * D-42: lo que antes hacía la "falla de demostración" desde adentro de la aplicación, ahora sólo existe en la prueba.
 * Corre la confirmación real (validar, cotizar, cobrar en la pasarela, confirmar, encolar y publicar) dentro de su
 * transacción y falla al final, antes del commit: la base y los mensajes se revierten y la compensación devuelve el
 * cobro. {@code @RunAs("PACIENTE")} porque la confirmación exige ese rol.
 */
@Stateless
@PermitAll
@RunAs("PACIENTE")
@DeclareRoles({"PACIENTE", "ADMINISTRATIVO"})
public class ConfirmacionQueFalla {

    @Inject
    private ProcesoConfirmacionTurno proceso;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno confirmarYFallarAntesDelCommit(Long turnoId, Long pacienteId, DatosPago datosPago) {
        Turno confirmado = proceso.confirmar(turnoId, pacienteId, datosPago);
        if (confirmado != null) {
            throw new IllegalStateException("Falla de prueba después de todos los pasos y antes del commit");
        }
        return null;
    }

}
