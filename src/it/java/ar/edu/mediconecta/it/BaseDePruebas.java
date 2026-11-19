package ar.edu.mediconecta.it;

import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.UserTransaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/** Lo que las pruebas de integración necesitan de la base real: transacciones, datos de prueba y consultas directas. */
@ApplicationScoped
public class BaseDePruebas {

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Resource(lookup = "java:jboss/UserTransaction")
    private UserTransaction transaccion;

    public EntityManager em() {
        return em;
    }

    /** Corre el bloque en una transacción y la confirma. */
    public <T> T enTransaccion(Callable<T> bloque) throws Exception {
        transaccion.begin();
        try {
            T resultado = bloque.call();
            transaccion.commit();
            return resultado;
        } catch (Exception e) {
            transaccion.rollback();
            throw e;
        }
    }

    /** Corre el bloque en una transacción y la revierte: la base queda como estaba. */
    public void yDeshacer(Callable<Void> bloque) throws Exception {
        transaccion.begin();
        try {
            bloque.call();
        } finally {
            transaccion.rollback();
        }
    }

    public static String sufijo() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100_000, 999_999));
    }

    public Profesional profesional(String sufijo) {
        Profesional profesional = new Profesional("Dra. Integración " + sufijo, "it.profesional." + sufijo + "@mediconecta.test",
                "sin-ingreso", "MN IT" + sufijo, "Clínica médica");
        em.persist(profesional);
        return profesional;
    }

    public Paciente paciente(String sufijo, String afiliado) {
        Paciente paciente = new Paciente("Paciente Integración " + sufijo, "it.paciente." + sufijo + "@mediconecta.test",
                "sin-ingreso", "OSDE 210", afiliado);
        em.persist(paciente);
        return paciente;
    }

    /** Sin obra social: paga el total del turno, así la confirmación cobra en la pasarela. */
    public Paciente pacienteParticular(String sufijo) {
        Paciente paciente = new Paciente("Paciente Integración " + sufijo, "it.paciente." + sufijo + "@mediconecta.test",
                "sin-ingreso", null, null);
        em.persist(paciente);
        return paciente;
    }

    /** Un turno retenido a nombre del paciente por 5 minutos, listo para confirmar. */
    public Turno turnoEnHold(Profesional profesional, Paciente paciente, LocalDateTime fechaHora) {
        Turno turno = new Turno(profesional, fechaHora);
        turno.reservar(paciente, LocalDateTime.now().plusMinutes(5));
        em.persist(turno);
        return turno;
    }

    public Object valor(String sql, Object parametro) {
        return em.createNativeQuery(sql).setParameter(1, parametro).getResultStream().findFirst().orElse(null);
    }

    public Turno turnoConfirmado(Profesional profesional, Paciente paciente, LocalDateTime fechaHora) {
        Turno turno = new Turno(profesional, fechaHora);
        turno.reservar(paciente, LocalDateTime.now().plusMinutes(5));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.PARTICULAR, null, null, "420101", new BigDecimal("32000"),
                new BigDecimal("32000"), BigDecimal.ZERO, EstadoAutorizacion.NO_REQUIERE));
        em.persist(turno);
        return turno;
    }

    /** Lo que guardó la columna, sin pasar por JPA ni por el conversor: lo que ve quien mira la base. */
    public String columna(String sql, Object id) {
        return (String) em.createNativeQuery(sql).setParameter(1, id).getSingleResult();
    }

    public long contar(String sql, Object parametro) {
        return ((Number) em.createNativeQuery(sql).setParameter(1, parametro).getSingleResult()).longValue();
    }

    /** Borra lo que dejó una prueba que tuvo que confirmar (las de mensajería), en el orden de las claves foráneas. */
    public void borrar(List<String> sentencias, Object parametro) throws Exception {
        enTransaccion(() -> {
            for (String sql : sentencias) {
                em.createNativeQuery(sql).setParameter(1, parametro).executeUpdate();
            }
            return null;
        });
    }
}
