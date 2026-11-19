package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.negocio.dto.FichaPaciente;
import jakarta.annotation.Priority;
import jakarta.decorator.Decorator;
import jakarta.decorator.Delegate;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import jakarta.interceptor.Interceptor;
import jakarta.security.enterprise.SecurityContext;

import java.security.Principal;

/**
 * Decorator de CDI (GoF; documento de diseño de patrones y seguridad, sección 4.8). Envuelve a
 * {@link ServicioDePacientes} sin cambiarlo y enmascara los datos clínicos de la ficha cuando quien la pide no puede
 * verlos: la regla de privacidad queda en un solo lugar, separada del negocio.
 *
 * <p>Sólo ve los datos clínicos un profesional con un turno confirmado con ese paciente: el diseño pedía el rol
 * PROFESIONAL, y le sumamos la misma regla de titularidad que ya cumple la historia clínica (SEC-D01), para que un
 * profesional no vea por la ficha lo que no puede ver en la historia. El administrativo ve la identificación y la
 * cobertura, que necesita para las autorizaciones y los reclamos a la obra social, pero nada clínico.
 *
 * <p>{@code @Priority} lo habilita en toda la aplicación sin declararlo en {@code beans.xml}.
 */
@Decorator
@Priority(Interceptor.Priority.APPLICATION)
public abstract class PacienteEnmascarado implements ServicioDePacientes {

    static final String ROL_CON_ACCESO = "PROFESIONAL";

    @Inject
    @Delegate
    @Any
    private ServicioDePacientes delegado;

    @Inject
    private SecurityContext seguridad;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private TurnoDAO turnoDAO;

    @Override
    public FichaPaciente ficha(Long pacienteId) {
        FichaPaciente ficha = delegado.ficha(pacienteId);
        return puedeVerDatosClinicos(pacienteId) ? ficha : ficha.sinDatosClinicos();
    }

    private boolean puedeVerDatosClinicos(Long pacienteId) {
        Principal quien = seguridad.getCallerPrincipal();
        if (quien == null || !seguridad.isCallerInRole(ROL_CON_ACCESO)) {
            return false;
        }
        return usuarioDAO.buscarPorEmail(quien.getName())
                .map(profesional -> turnoDAO.existeConfirmadoEntre(profesional.getId(), pacienteId))
                .orElse(false);
    }
}
