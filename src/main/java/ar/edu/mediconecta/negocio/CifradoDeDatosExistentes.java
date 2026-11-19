package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.seguridad.CifradorDeColumnas;
import ar.edu.mediconecta.seguridad.ClaveDeCifrado;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.UserTransaction;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Al desplegar, deja cifradas las columnas sensibles (ADR-0022) también en las filas que se guardaron antes del
 * cifrado. Dos pasos, los dos idempotentes:
 * <ol>
 *   <li>ensancha las columnas si todavía tienen el largo viejo ({@code hbm2ddl=update} crea las nuevas con el largo
 *       correcto, pero no modifica las que ya existen);</li>
 *   <li>cifra los valores que no empiezan con {@value CifradorDeColumnas#PREFIJO}.</li>
 * </ol>
 * Antes que nada exige la clave: sin ella la aplicación no despliega, con un mensaje que dice cómo configurarla.
 * Si la base no responde, despliega igual (como {@link InicializadorDeAdministrador}) y se reintenta al próximo
 * despliegue; las filas viejas se siguen leyendo bien mientras tanto, porque el conversor deja pasar el texto en claro.
 */
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class CifradoDeDatosExistentes {

    private static final Logger LOG = Logger.getLogger(CifradoDeDatosExistentes.class.getName());

    /**
     * Cada columna cifrada, con el largo que necesita el dato cifrado (el texto más largo en UTF-8, más IV, etiqueta y
     * Base64) y sus consultas, escritas enteras: ningún nombre de tabla o columna se arma con texto.
     */
    static final List<Columna> COLUMNAS = List.of(
            new Columna("usuario", "nro_afiliado", 256,
                    "ALTER TABLE usuario ALTER COLUMN nro_afiliado TYPE varchar(256)",
                    "SELECT id, nro_afiliado FROM usuario WHERE nro_afiliado IS NOT NULL AND nro_afiliado NOT LIKE ?1",
                    "UPDATE usuario SET nro_afiliado = ?1 WHERE id = ?2"),
            new Columna("entrada_clinica", "descripcion", 8100,
                    "ALTER TABLE entrada_clinica ALTER COLUMN descripcion TYPE varchar(8100)",
                    "SELECT id, descripcion FROM entrada_clinica WHERE descripcion IS NOT NULL AND descripcion NOT LIKE ?1",
                    "UPDATE entrada_clinica SET descripcion = ?1 WHERE id = ?2"));

    record Columna(String tabla, String nombre, int largo, String ensanchar, String pendientes, String actualizar) {
    }

    private static final String LARGO_ACTUAL = "SELECT character_maximum_length FROM information_schema.columns"
            + " WHERE table_schema = current_schema() AND table_name = ?1 AND column_name = ?2";

    @PersistenceContext(unitName = "mediconectaPU")
    private EntityManager em;

    @Resource
    private UserTransaction transaccion;

    @PostConstruct
    void cifrarLoPendiente() {
        CifradorDeColumnas cifrador = ClaveDeCifrado.cifrador(); // sin clave, el despliegue falla acá
        for (Columna columna : COLUMNAS) {
            try {
                transaccion.begin();
                ensanchar(columna);
                int cifradas = cifrarFilas(columna, cifrador);
                transaccion.commit();
                if (cifradas > 0) {
                    LOG.info(() -> "Cifrado de columnas: " + cifradas + " valor(es) de " + columna.tabla() + "."
                            + columna.nombre() + " pasaron a AES-GCM.");
                }
            } catch (Exception e) {
                deshacer();
                LOG.log(Level.WARNING, "No se pudo completar el cifrado de " + columna.tabla() + "." + columna.nombre()
                        + "; se reintenta al próximo despliegue.", e);
            }
        }
    }

    private void ensanchar(Columna columna) {
        Number largoActual = (Number) em.createNativeQuery(LARGO_ACTUAL)
                .setParameter(1, columna.tabla())
                .setParameter(2, columna.nombre())
                .getResultStream().findFirst().orElse(null);
        if (largoActual != null && largoActual.intValue() < columna.largo()) {
            em.createNativeQuery(columna.ensanchar()).executeUpdate();
            LOG.info(() -> "Cifrado de columnas: " + columna.tabla() + "." + columna.nombre() + " pasó de varchar("
                    + largoActual + ") a varchar(" + columna.largo() + ").");
        }
    }

    private int cifrarFilas(Columna columna, CifradorDeColumnas cifrador) {
        @SuppressWarnings("unchecked")
        List<Object[]> pendientes = em.createNativeQuery(columna.pendientes())
                .setParameter(1, CifradorDeColumnas.PREFIJO + "%")
                .getResultList();
        for (Object[] fila : pendientes) {
            em.createNativeQuery(columna.actualizar())
                    .setParameter(1, cifrador.cifrar((String) fila[1]))
                    .setParameter(2, ((Number) fila[0]).longValue())
                    .executeUpdate();
        }
        return pendientes.size();
    }

    private void deshacer() {
        try {
            transaccion.rollback();
        } catch (Exception yaTerminada) {
            LOG.log(Level.FINE, "No había transacción para revertir.", yaTerminada);
        }
    }
}
