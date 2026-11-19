package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.auditoria.Auditado;
import ar.edu.mediconecta.dao.HistoriaClinicaDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.EntradaClinica;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.dto.FichaPaciente;
import ar.edu.mediconecta.negocio.dto.FichaPaciente.EntradaDeFicha;
import ar.edu.mediconecta.negocio.excepciones.DatosDeUsuarioInvalidosException;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;

/**
 * El componente concreto del Decorator (diseño, sección 4.8): arma la ficha completa y no sabe nada de quién la
 * pide, más allá de los roles que la pueden pedir. Qué parte ve cada uno lo decide {@link PacienteEnmascarado}, así
 * la regla de privacidad queda en un solo lugar y fuera del negocio. Cada consulta queda en la auditoría.
 */
@Stateless
@DeclareRoles({"PROFESIONAL", "ADMINISTRATIVO"})
public class ServicioDePacientesBean implements ServicioDePacientes {

    static final String RECURSO = "ficha_paciente";

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private HistoriaClinicaDAO historiaClinicaDAO;

    @Override
    @RolesAllowed({"PROFESIONAL", "ADMINISTRATIVO"})
    @Auditado(recurso = RECURSO)
    public FichaPaciente ficha(Long pacienteId) {
        Usuario usuario = pacienteId == null ? null : usuarioDAO.buscarPorId(pacienteId);
        if (!(usuario instanceof Paciente paciente)) {
            throw new DatosDeUsuarioInvalidosException("El paciente no existe.");
        }
        HistoriaClinica historia = historiaClinicaDAO.buscarPorPaciente(pacienteId);
        List<EntradaClinica> entradas = historia == null ? List.of() : historia.getEntradas();
        List<EntradaDeFicha> ultimas = entradas.stream()
                .limit(ENTRADAS_EN_LA_FICHA)
                .map(e -> new EntradaDeFicha(e.getFecha(), e.getTipo() == null ? "" : e.getTipo().name(),
                        e.getProfesional().getNombreCompleto(), e.getDescripcion()))
                .toList();
        return new FichaPaciente(paciente.getId(), paciente.getNombreCompleto(), paciente.getEmail(),
                paciente.getObraSocial(), paciente.getNroAfiliado(), ultimas, entradas.size());
    }
}
