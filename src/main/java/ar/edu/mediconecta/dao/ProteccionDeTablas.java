package ar.edu.mediconecta.dao;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import java.util.List;
import java.util.logging.Logger;

/**
 * Al desplegar, habilita Row Level Security en toda tabla del esquema {@code public} que no la tenga: lo mismo que
 * {@code sql/02-habilitar-rls.sql}, pero sin depender de que alguien se acuerde de correrlo. Hibernate crea las tablas
 * nuevas sin RLS ({@code hbm2ddl.auto=update}) y Supabase publica ese esquema por su API REST: sin RLS, una tabla
 * nueva quedaría legible con la clave anónima del proyecto.
 *
 * <p>MediConecta se conecta como dueña de las tablas, a la que RLS no se le aplica: para la app nada cambia. Si la
 * base no es PostgreSQL o el usuario no tiene permiso, lo avisa en el log y la app despliega igual. La transacción la
 * maneja el propio bean (BMT), para poder revertirla y seguir.
 */
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class ProteccionDeTablas {

    private static final Logger LOG = Logger.getLogger(ProteccionDeTablas.class.getName());

    private static final String TABLAS_SIN_RLS =
            "select tablename from pg_tables where schemaname = 'public' and not rowsecurity order by tablename";

    private static final String HABILITAR_RLS = """
            DO $$
            DECLARE
                tabla record;
            BEGIN
                FOR tabla IN SELECT schemaname, tablename FROM pg_tables WHERE schemaname = 'public' AND NOT rowsecurity
                LOOP
                    EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', tabla.schemaname, tabla.tablename);
                END LOOP;
            END $$""";

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Resource
    private UserTransaction transaccion;

    @PostConstruct
    @SuppressWarnings("unchecked")
    void habilitarRls() {
        try {
            transaccion.begin();
            List<String> sinRls = em.createNativeQuery(TABLAS_SIN_RLS).getResultList();
            if (!sinRls.isEmpty()) {
                em.createNativeQuery(HABILITAR_RLS).executeUpdate();
                LOG.info("Row Level Security habilitado en " + sinRls.size() + " tabla(s) que no lo tenían: "
                        + String.join(", ", sinRls) + ".");
            }
            transaccion.commit();
        } catch (Exception e) {
            deshacer();
            LOG.warning("No se pudo verificar Row Level Security en las tablas (¿la base no es PostgreSQL?): " + e);
        }
    }

    private void deshacer() {
        try {
            if (transaccion.getStatus() != Status.STATUS_NO_TRANSACTION) {
                transaccion.rollback();
            }
        } catch (Exception ignorada) {
            // no hay nada más que hacer: la transacción ya no existe
        }
    }
}
