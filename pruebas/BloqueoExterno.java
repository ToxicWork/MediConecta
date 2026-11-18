import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

/**
 * Otra transacción que modifica el turno y lo retiene un rato, como la autorización asincrónica de la obra social:
 * sube la versión, avisa "bloqueado", espera y hace commit. Las credenciales llegan por variables de entorno
 * (MC_URL, MC_USUARIO, MC_CLAVE) y nunca se imprimen.
 *
 * Uso: java -cp postgresql.jar BloqueoExterno.java <turnoId> <milisegundos>
 */
public class BloqueoExterno {
    public static void main(String[] args) throws Exception {
        long turnoId = Long.parseLong(args[0]);
        long espera = Long.parseLong(args[1]);
        try (Connection conexion = DriverManager.getConnection(System.getenv("MC_URL"), System.getenv("MC_USUARIO"),
                System.getenv("MC_CLAVE"))) {
            conexion.setAutoCommit(false);
            try (PreparedStatement cambio = conexion.prepareStatement("update turno set version = version + 1 where id = ?")) {
                cambio.setLong(1, turnoId);
                System.out.println("bloqueado " + cambio.executeUpdate());
                System.out.flush();
            }
            Thread.sleep(espera);
            conexion.commit();
            System.out.println("liberado");
        }
    }
}
