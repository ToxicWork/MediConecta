package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.HistoriaClinicaDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.EntradaClinica;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.TipoEntrada;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

/**
 * Datos de alta sensibilidad: exige seguridad declarativa por rol. Cada operación
 * es independiente y no conserva estado entre invocaciones, por eso es {@code @Stateless}.
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL"})
public class ServicioDeHistoriaClinicaBean implements ServicioDeHistoriaClinica {

    @Inject
    private HistoriaClinicaDAO historiaClinicaDAO;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private TurnoDAO turnoDAO;

    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    public HistoriaClinica obtenerDePaciente(Long pacienteId) {
        HistoriaClinica historia = historiaClinicaDAO.buscarPorPaciente(pacienteId);
        if (historia == null) {
            historia = new HistoriaClinica(usuarioDAO.obtenerReferenciaPaciente(pacienteId));
            historiaClinicaDAO.guardar(historia);
        }
        return historia;
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    public EntradaClinica agregarEntrada(Long pacienteId, Long profesionalId, Long turnoId, TipoEntrada tipo, String descripcion) {
        HistoriaClinica historia = obtenerDePaciente(pacienteId);
        Turno turno = turnoId == null ? null : turnoDAO.buscarPorId(turnoId);
        EntradaClinica entrada = new EntradaClinica(usuarioDAO.obtenerReferenciaProfesional(profesionalId), turno, tipo, descripcion);
        historia.agregarEntrada(entrada);
        historiaClinicaDAO.agregarEntrada(entrada);
        return entrada;
    }
}
