import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Mira directamente en la base lo que guarda la aplicación (ADR-0022), como lo vería alguien que se lleva una copia:
 * el n.º de afiliado y las entradas de la historia clínica del paciente tienen que estar cifrados y no contener el
 * texto en claro. Las credenciales llegan por variables de entorno (MC_URL, MC_USUARIO, MC_CLAVE) y nunca se
 * imprimen; tampoco se imprime ningún valor de la base, sólo si cumple o no.
 *
 * Uso: java -cp postgresql.jar VerificarCifrado.java <email del paciente> <n.º de afiliado> <texto de la entrada>
 * Imprime líneas "clave=valor" que lee pruebas/prueba_datos_protegidos.py.
 */
public class VerificarCifrado {

    private static final String PREFIJO = "enc:v1:";

    public static void main(String[] args) throws SQLException {
        String email = args[0];
        String afiliado = args[1];
        String entrada = args[2];
        try (Connection c = DriverManager.getConnection(System.getenv("MC_URL"), System.getenv("MC_USUARIO"),
                System.getenv("MC_CLAVE"))) {
            try (PreparedStatement p = c.prepareStatement("select nro_afiliado from usuario where email = ?")) {
                p.setString(1, email);
                try (ResultSet r = p.executeQuery()) {
                    String guardado = r.next() ? r.getString(1) : null;
                    System.out.println("afiliado_cifrado=" + (guardado != null && guardado.startsWith(PREFIJO)));
                    System.out.println("afiliado_en_claro=" + (guardado != null && guardado.contains(afiliado)));
                }
            }
            try (PreparedStatement p = c.prepareStatement("select e.descripcion from entrada_clinica e"
                    + " join historia_clinica h on h.id = e.historia_clinica_id join usuario u on u.id = h.paciente_id"
                    + " where u.email = ?")) {
                p.setString(1, email);
                int total = 0;
                int cifradas = 0;
                boolean enClaro = false;
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        total++;
                        cifradas += r.getString(1).startsWith(PREFIJO) ? 1 : 0;
                        enClaro |= r.getString(1).contains(entrada);
                    }
                }
                System.out.println("entradas=" + total);
                System.out.println("entradas_cifradas=" + cifradas);
                System.out.println("entrada_en_claro=" + enClaro);
            }
            long pendientes = contar(c, "select count(*) from usuario where nro_afiliado is not null and nro_afiliado not like 'enc:v1:%'")
                    + contar(c, "select count(*) from entrada_clinica where descripcion not like 'enc:v1:%'");
            System.out.println("pendientes=" + pendientes);
            System.out.println("largo_afiliado=" + contar(c, "select character_maximum_length from information_schema.columns"
                    + " where table_schema = current_schema() and table_name = 'usuario' and column_name = 'nro_afiliado'"));
            System.out.println("largo_descripcion=" + contar(c, "select character_maximum_length from information_schema.columns"
                    + " where table_schema = current_schema() and table_name = 'entrada_clinica' and column_name = 'descripcion'"));
        }
    }

    private static long contar(Connection c, String sql) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            return r.next() ? r.getLong(1) : -1;
        }
    }
}
