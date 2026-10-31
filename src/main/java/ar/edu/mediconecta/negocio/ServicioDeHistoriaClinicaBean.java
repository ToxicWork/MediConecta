package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.auditoria.Auditado;
import ar.edu.mediconecta.dao.HistoriaClinicaDAO;
import ar.edu.mediconecta.dao.RegistroAccesoDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.EntradaClinica;
import ar.edu.mediconecta.modelo.HistoriaClinica;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.RegistroAcceso;
import ar.edu.mediconecta.modelo.TipoEntrada;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.dto.AccesoHistoriaDTO;
import ar.edu.mediconecta.negocio.excepciones.AccesoDenegadoException;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Datos de alta sensibilidad. Dos niveles de seguridad:
 * <ul>
 *   <li>declarativa: {@code @RolesAllowed} decide qué rol puede ejecutar cada operación;</li>
 *   <li>programática: la titularidad. Un paciente sólo ve su historia y un profesional sólo la de pacientes con
 *       un turno confirmado con él (antes esto se controlaba sólo en la vista; ahora lo garantiza el servicio).</li>
 * </ul>
 * Cada acceso queda registrado por {@code AuditoriaInterceptor} ({@code @Auditado}, Intercepting Filter).
 */
@Stateless
@DeclareRoles({"PACIENTE", "PROFESIONAL"})
public class ServicioDeHistoriaClinicaBean implements ServicioDeHistoriaClinica {

    static final String RECURSO = "historia_clinica";

    @Inject
    private HistoriaClinicaDAO historiaClinicaDAO;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private RegistroAccesoDAO registroAccesoDAO;

    @Resource
    private SessionContext contexto;

    @Override
    @RolesAllowed({"PACIENTE", "PROFESIONAL"})
    @Auditado(recurso = RECURSO)
    public HistoriaClinica obtenerDePaciente(Long pacienteId) {
        verificarTitularidad(pacienteId);
        return obtenerOCrear(pacienteId);
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    @Auditado(recurso = RECURSO)
    public EntradaClinica agregarEntrada(Long pacienteId, Long turnoId, TipoEntrada tipo, String descripcion) {
        Usuario profesional = verificarTitularidad(pacienteId);
        HistoriaClinica historia = obtenerOCrear(pacienteId);
        Turno turno = turnoId == null ? null : turnoDAO.buscarPorId(turnoId);
        EntradaClinica entrada = new EntradaClinica((Profesional) profesional, turno, tipo, descripcion);
        historia.agregarEntrada(entrada);
        historiaClinicaDAO.agregarEntrada(entrada);
        return entrada;
    }

    @Override
    @RolesAllowed("PACIENTE")
    public List<AccesoHistoriaDTO> accesosAMiHistoria() {
        Usuario paciente = usuarioActual();
        return registroAccesoDAO.buscarDeRecursoConNombre(RECURSO, paciente.getId(), paciente.getEmail(), 15).stream()
                .map(fila -> {
                    RegistroAcceso registro = (RegistroAcceso) fila[0];
                    return new AccesoHistoriaDTO(registro.getFecha(), (String) fila[1],
                            "agregarEntrada".equals(registro.getOperacion()) ? "Agregó una entrada" : "Consultó la historia",
                            registro.getResultado());
                })
                .toList();
    }

    private HistoriaClinica obtenerOCrear(Long pacienteId) {
        HistoriaClinica historia = historiaClinicaDAO.buscarPorPaciente(pacienteId);
        if (historia == null) {
            historia = new HistoriaClinica(usuarioDAO.obtenerReferenciaPaciente(pacienteId));
            historiaClinicaDAO.guardar(historia);
        }
        return historia;
    }

    private Usuario verificarTitularidad(Long pacienteId) {
        Usuario actual = usuarioActual();
        boolean permitido = actual instanceof Paciente
                ? actual.getId().equals(pacienteId)
                : actual instanceof Profesional && turnoDAO.existeConfirmadoEntre(actual.getId(), pacienteId);
        if (!permitido) {
            throw new AccesoDenegadoException("No tenés acceso a la historia clínica de este paciente.");
        }
        return actual;
    }

    private Usuario usuarioActual() {
        return usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName())
                .orElseThrow(() -> new AccesoDenegadoException("Usuario no identificado."));
    }
}
