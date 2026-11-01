package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import ar.edu.mediconecta.negocio.factory.UsuarioFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.inject.Inject;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administrador por defecto: como el registro público sólo crea pacientes, alguien tiene que poder dar de
 * alta al primer profesional o administrativo. Al desplegar, si no existe {@value #EMAIL}, se crea con una
 * contraseña inicial conocida (documentada en SETUP.md) y marcada como temporal: el primer ingreso obliga a
 * cambiarla, así que publicarla en el repositorio no deja una puerta abierta.
 *
 * <p>La contraseña inicial se puede cambiar con la propiedad de sistema {@code mediconecta.admin.password}
 * (por ejemplo {@code standalone.bat -Dmediconecta.admin.password=...}). {@code @Singleton @Startup}: lo
 * ejecuta el contenedor una sola vez al desplegar. La transacción la maneja el propio bean (BMT) para poder
 * revertirla y seguir: si la base no responde al arrancar, la app despliega igual y se reintenta la próxima vez.
 */
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class InicializadorDeAdministrador {

    static final String EMAIL = "admin@mediconecta.test";
    static final String PASSWORD_INICIAL = "Admin.2026";

    private static final Logger LOG = Logger.getLogger(InicializadorDeAdministrador.class.getName());

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private UsuarioFactory usuarioFactory;

    @Inject
    private Pbkdf2PasswordHash passwordHash;

    @Resource
    private UserTransaction transaccion;

    @PostConstruct
    void asegurarAdministrador() {
        try {
            transaccion.begin();
            if (!usuarioDAO.existeEmail(EMAIL)) {
                passwordHash.initialize(Collections.emptyMap());
                String password = System.getProperty("mediconecta.admin.password", PASSWORD_INICIAL);
                Usuario admin = usuarioFactory.crear(Rol.ADMINISTRATIVO,
                        new DatosRegistro("Administrador", EMAIL, null, null, null, null),
                        passwordHash.generate(password.toCharArray()));
                admin.exigirCambioDePassword();
                usuarioDAO.guardar(admin);
                LOG.info("Se creó el administrador por defecto " + EMAIL + " (debe cambiar la contraseña al ingresar).");
            }
            transaccion.commit();
        } catch (Exception e) {
            // Sin base (Supabase pausado, red caída) la app igual tiene que desplegar: se reintenta al próximo arranque.
            deshacer();
            LOG.log(Level.WARNING, "No se pudo verificar el administrador por defecto; se reintenta al próximo despliegue.", e);
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
