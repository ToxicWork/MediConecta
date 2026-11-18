package ar.edu.mediconecta.dao;

import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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

    /**
     * Lee el turno y bloquea su fila hasta el fin de la transacción (SELECT ... FOR UPDATE). Lo usan las operaciones
     * que lo modifican: si otra lo está modificando, ésta espera y lee la versión nueva, en lugar de chocar al hacer el
     * commit con el bloqueo optimista (@Version) y terminar en un error 500 (D-36).
     */
    public Turno buscarPorIdParaActualizar(Long id) {
        return em.find(Turno.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Relee un turno ya cargado y bloquea su fila: después de una llamada externa larga, justo antes de escribir (D-36). */
    public void refrescarParaActualizar(Turno turno) {
        em.refresh(turno, LockModeType.PESSIMISTIC_WRITE);
    }

    public Turno actualizar(Turno turno) {
        return em.merge(turno);
    }

    /** Escribe ya los cambios pendientes: después de esto, la versión del turno es la nueva (para su ETag). */
    public void sincronizar() {
        em.flush();
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

    /**
     * Agenda del paciente y del profesional: los turnos confirmados en los que participa el usuario y que todavía no
     * empezaron, del más próximo al más lejano. Los que ya empezaron no se pueden cancelar (patrón State) y salen de
     * la agenda; siguen en la base como historial (pagos, historia clínica, API).
     */
    public List<Turno> buscarProximosConfirmadosDeUsuario(Long usuarioId, LocalDateTime ahora) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE (t.paciente.id = :usuarioId OR t.profesional.id = :usuarioId) "
                                + "AND t.estado = :estado AND t.fechaHora > :ahora ORDER BY t.fechaHora, t.id", Turno.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .setParameter("ahora", ahora)
                .getResultList();
    }

    /**
     * Pantalla Turnos del administrativo: una página de los turnos confirmados que todavía no pasaron, del más
     * próximo al más lejano, de todos los profesionales o de uno ({@code profesionalId} {@code null} = todos).
     */
    public List<Turno> buscarProximosConfirmados(Long profesionalId, LocalDateTime ahora, int desde, int cantidad) {
        return em.createQuery("SELECT t FROM Turno t WHERE t.estado = :estado AND t.fechaHora > :ahora "
                                + "AND (:profesionalId IS NULL OR t.profesional.id = :profesionalId) "
                                + "ORDER BY t.fechaHora, t.id", Turno.class)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .setParameter("ahora", ahora)
                .setParameter("profesionalId", profesionalId)
                .setFirstResult(desde)
                .setMaxResults(cantidad)
                .getResultList();
    }

    public int contarProximosConfirmados(Long profesionalId, LocalDateTime ahora) {
        return em.createQuery("SELECT COUNT(t) FROM Turno t WHERE t.estado = :estado AND t.fechaHora > :ahora "
                                + "AND (:profesionalId IS NULL OR t.profesional.id = :profesionalId)", Long.class)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .setParameter("ahora", ahora)
                .setParameter("profesionalId", profesionalId)
                .getSingleResult().intValue();
    }

    public List<Turno> buscarConfirmadosDeProfesional(Long profesionalId) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE t.profesional.id = :profesionalId "
                                + "AND t.estado = :estado ORDER BY t.fechaHora DESC", Turno.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .getResultList();
    }

    /**
     * Turnos de telemedicina confirmados en los que participa el usuario (como paciente o profesional) que todavía
     * importan: los de {@code desde} en adelante y los anteriores con la sala abierta. Los de sala cerrada ya terminaron.
     */
    public List<Turno> buscarVirtualesConfirmadosDeUsuario(Long usuarioId, LocalDateTime desde) {
        return em.createQuery(
                        "SELECT t FROM Turno t WHERE (t.paciente.id = :usuarioId OR t.profesional.id = :usuarioId) "
                                + "AND t.estado = :estado AND t.modalidad = :modalidad AND t.salaCerradaEn IS NULL "
                                + "AND (t.fechaHora >= :desde OR t.salaId IS NOT NULL) ORDER BY t.fechaHora", Turno.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("estado", EstadoTurno.CONFIRMADO)
                .setParameter("modalidad", Modalidad.TELEMEDICINA)
                .setParameter("desde", desde)
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

    /**
     * Regla "sin horarios repetidos" al publicar (D-23): otro turno vigente del profesional en la misma fecha y hora.
     * Los cancelados no cuentan: si el profesional canceló, puede volver a ofrecer ese horario.
     */
    public boolean profesionalTieneHorarioEn(Long profesionalId, LocalDateTime fechaHora) {
        return em.createQuery("SELECT COUNT(t) FROM Turno t WHERE t.profesional.id = :profesionalId "
                        + "AND t.fechaHora = :fechaHora AND t.estado <> :cancelado", Long.class)
                .setParameter("profesionalId", profesionalId)
                .setParameter("fechaHora", fechaHora)
                .setParameter("cancelado", EstadoTurno.CANCELADO)
                .getSingleResult() > 0;
    }
}
