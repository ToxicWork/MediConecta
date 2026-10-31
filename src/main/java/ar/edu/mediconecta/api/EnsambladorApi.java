package ar.edu.mediconecta.api;

import ar.edu.mediconecta.api.dto.ProfesionalApi;
import ar.edu.mediconecta.api.dto.TurnoApi;
import ar.edu.mediconecta.api.dto.TurnoDisponibleApi;
import ar.edu.mediconecta.modelo.Prestacion;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;

/** Transfer Object Assembler: arma los DTO de la API a partir de las entidades, que nunca salen de la aplicación. */
final class EnsambladorApi {

    private EnsambladorApi() {
    }

    static ProfesionalApi profesional(Profesional p) {
        return new ProfesionalApi(p.getId(), p.getNombreCompleto(), p.getEspecialidad(), p.getMatricula());
    }

    static TurnoDisponibleApi disponible(Turno t, Prestacion prestacion) {
        return new TurnoDisponibleApi(t.getId(), t.getFechaHora(), t.getModalidad().name(), prestacion.getCodigo(),
                prestacion.getPrecio());
    }

    static TurnoApi turno(Turno t, Usuario quienConsulta) {
        boolean esPaciente = t.getPaciente() != null && t.getPaciente().getId().equals(quienConsulta.getId());
        return new TurnoApi(t.getId(), t.getFechaHora(), t.getModalidad().name(), t.getEstado().name(),
                t.getProfesional().getNombreCompleto(), t.getProfesional().getEspecialidad(),
                t.getPaciente() == null ? null : t.getPaciente().getNombreCompleto(),
                t.getCobertura() == null ? null : t.getCobertura().name(), t.getEstadoAutorizacion().name(),
                t.getCodigoAutorizacion(), t.getImportePaciente(),
                esPaciente ? t.getSalaUrlPaciente() : t.getSalaUrlProfesional());
    }
}
