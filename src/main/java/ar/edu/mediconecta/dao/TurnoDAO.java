package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class TurnoDAO {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    public void guardar(Turno turno) {
        em.persist(turno);
    }

    public Turno buscarPorId(Long id) {
        return em.find(Turno.class, id);
    }

    public Turno actualizar(Turno turno) {
        return em.merge(turno);
    }

    /** Solo horarios futuros: un turno disponible que ya pasó no se puede reservar. */
    public List<Turno> buscarDisponibles(Long profesionalId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.profesional.id = :profesionalId "
                                + "AND t.estado = :estado AND t.fechaHora > :ahora ORDER BY t.fechaHora", Turno.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("estado", EstadoTurno.DISPONIBLE)
                .setParameter("ahora", LocalDateTime.now())
                .getResultList();
    }

    public List<Turno> buscarConfirmadosDePaciente(Long pacienteId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.paciente.id = :pacienteId "
                                + "AND t.estado = :estado ORDER BY t.fechaHora DESC", Turno.class)
                .setParameter("pacienteId", pacienteId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .getResultList();
    }

    public List<Turno> buscarConfirmadosDeProfesional(Long profesionalId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.profesional.id = :profesionalId "
                                + "AND t.estado = :estado ORDER BY t.fechaHora DESC", Turno.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .getResultList();
    }

    /** Turnos de telemedicina confirmados en los que participa el usuario (como paciente o profesional). */
    public List<Turno> buscarVirtualesConfirmadosDeUsuario(Long usuarioId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE (t.paciente.id = :usuarioId OR t.profesional.id = :usuarioId) "
                                + "AND t.estado = :estado AND t.modalidad = :modalidad ORDER BY t.fechaHora", Turno.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .setParameter("modalidad", Modalidad.TELEMEDICINA)
                .getResultList();
    }

    /** Turnos (de cualquier estado salvo disponibles) del usuario, para la API REST. */
    public List<Turno> buscarDeUsuario(Long usuarioId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE (t.paciente.id = :usuarioId OR t.profesional.id = :usuarioId) "
                                + "AND t.estado <> :disponible ORDER BY t.fechaHora DESC", Turno.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("disponible", EstadoTurno.DISPONIBLE)
                .getResultList();
    }

    /** Regla "sin superposición": otro turno confirmado del mismo paciente en el mismo horario. */
    public boolean pacienteTieneConfirmadoEn(Long pacienteId, LocalDateTime fechaHora, Long excluirTurnoId) {
        return em.createQuery(
                        "SELECT COUNT(t) FROM Turno t WHERE t.paciente.id = :pacienteId AND t.estado = :estado "
                                + "AND t.fechaHora = :fechaHora AND t.id <> :excluir", Long.class)
                .setParameter("pacienteId", pacienteId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .setParameter("fechaHora", fechaHora)
                .setParameter("excluir", excluirTurnoId == null ? -1L : excluirTurnoId)
                .getSingleResult() > 0;
    }

    /** Titularidad en historia clínica: el profesional sólo accede a pacientes con turno confirmado con él. */
    public boolean existeConfirmadoEntre(Long profesionalId, Long pacienteId) {
        return em.createQuery(
                        "SELECT COUNT(t) FROM Turno t WHERE t.profesional.id = :profesionalId "
                                + "AND t.paciente.id = :pacienteId AND t.estado = :estado", Long.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("pacienteId", pacienteId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .getSingleResult() > 0;
    }

    /**
     * Vencimiento de la disponibilidad: borra los horarios publicados que nadie tomó y cuya fecha ya pasó.
     * Sólo los DISPONIBLE: confirmados, cancelados y en espera son historial o están en uso. Por las dudas
     * respeta las claves foráneas: si un horario libre quedó referenciado (por ejemplo, el pago compensado de
     * una confirmación fallida), no se borra; igual nunca se ofrece porque {@link #buscarDisponibles} filtra
     * por fecha.
     *
     * @return cantidad de horarios eliminados
     */
    public int eliminarDisponiblesVencidos(LocalDateTime ahora) {
        return em.createQuery(
                        "DELETE FROM Turno t WHERE t.estado = :estado AND t.fechaHora <= :ahora "
                                + "AND NOT EXISTS (SELECT 1 FROM Pago p WHERE p.turno = t) "
                                + "AND NOT EXISTS (SELECT 1 FROM SolicitudAutorizacion s WHERE s.turno = t) "
                                + "AND NOT EXISTS (SELECT 1 FROM ReclamoObraSocial r WHERE r.turno = t) "
                                + "AND NOT EXISTS (SELECT 1 FROM EntradaClinica e WHERE e.turno = t)")
                .setParameter("estado", EstadoTurno.DISPONIBLE)
                .setParameter("ahora", ahora)
                .executeUpdate();
    }

    public List<Turno> buscarHoldsVencidos(LocalDateTime ahora) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.estado = :estado AND t.holdVenceEn IS NOT NULL "
                                + "AND t.holdVenceEn < :ahora", Turno.class)
                .setParameter("estado", EstadoTurno.HOLD)
                .setParameter("ahora", ahora)
                .getResultList();
    }
}
