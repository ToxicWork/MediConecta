package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.excepciones.DatosDeUsuarioInvalidosException;
import ar.edu.mediconecta.negocio.factory.DatosRegistro;
import ar.edu.mediconecta.negocio.factory.UsuarioFactory;
import ar.edu.mediconecta.seguridad.Enmascarador;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionManagement;
import jakarta.ejb.TransactionManagementType;
import jakarta.inject.Inject;
import ar.edu.mediconecta.seguridad.ParametrosDeHash;
import jakarta.security.enterprise.identitystore.Pbkdf2PasswordHash;
import jakarta.transaction.Status;
import jakarta.transaction.UserTransaction;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Administrador por defecto: como el registro público sólo crea pacientes, alguien tiene que poder dar de
 * alta al primer profesional o administrativo. Al desplegar, si no existe {@value #EMAIL}, se crea con la
 * contraseña inicial que da la configuración del servidor, marcada como temporal: el primer ingreso obliga a
 * cambiarla.
 *
 * <p>La contraseña inicial nunca está en el código ni en el repositorio (G6): sale de la propiedad de sistema
 * {@value #PROPIEDAD_CONTRASENA_INICIAL} o, si no está, de la variable de entorno
 * {@value #VARIABLE_CONTRASENA_INICIAL}, y tiene que cumplir la {@link PoliticaDeContrasenas}. Sin ninguna de
 * las dos, el administrador no se crea y el log lo avisa (sin la contraseña). {@code @Singleton @Startup}: lo
 * ejecuta el contenedor una sola vez al desplegar. La transacción la maneja el propio bean (BMT) para poder
 * revertirla y seguir: si la base no responde al arrancar, la app despliega igual y se reintenta la próxima vez.
 */
@Singleton
@Startup
@TransactionManagement(TransactionManagementType.BEAN)
public class InicializadorDeAdministrador {

    static final String EMAIL = "admin@mediconecta.test";
    static final String PROPIEDAD_CONTRASENA_INICIAL = "mediconecta.admin.password";
    static final String VARIABLE_CONTRASENA_INICIAL = "MEDICONECTA_ADMIN_PASSWORD";

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
                crearAdministrador();
            }
            transaccion.commit();
        } catch (Exception e) {
            // Sin base (Supabase pausado, red caída) la app igual tiene que desplegar: se reintenta al próximo arranque.
            deshacer();
            LOG.log(Level.WARNING, "No se pudo verificar el administrador por defecto; se reintenta al próximo despliegue.", e);
        }
    }

    private void crearAdministrador() {
        String inicial;
        try {
            inicial = contrasenaInicial(System.getProperty(PROPIEDAD_CONTRASENA_INICIAL),
                    System.getenv(VARIABLE_CONTRASENA_INICIAL));
        } catch (IllegalStateException | DatosDeUsuarioInvalidosException sinContrasena) {
            LOG.warning("No se creó el administrador por defecto " + Enmascarador.email(EMAIL) + ": "
                    + sinContrasena.getMessage());
            return;
        }
        passwordHash.initialize(ParametrosDeHash.PBKDF2);
        Usuario admin = usuarioFactory.crear(Rol.ADMINISTRATIVO,
                new DatosRegistro("Administrador", EMAIL, null, null, null, null),
                passwordHash.generate(inicial.toCharArray()));
        admin.exigirCambioDePassword();
        usuarioDAO.guardar(admin);
        LOG.info("Se creó el administrador por defecto " + Enmascarador.email(EMAIL)
                + " (debe cambiar la contraseña al ingresar).");
    }

    /**
     * La contraseña inicial configurada: la de la propiedad de sistema o, si no está, la de la variable de entorno.
     *
     * @throws IllegalStateException si no hay ninguna de las dos
     * @throws DatosDeUsuarioInvalidosException si la configurada no cumple la política de contraseñas
     */
    static String contrasenaInicial(String deLaPropiedad, String deLaVariable) {
        String configurada = deLaPropiedad != null && !deLaPropiedad.isBlank() ? deLaPropiedad : deLaVariable;
        if (configurada == null || configurada.isBlank()) {
            throw new IllegalStateException("falta su contraseña inicial (variable de entorno "
                    + VARIABLE_CONTRASENA_INICIAL + " o propiedad de sistema " + PROPIEDAD_CONTRASENA_INICIAL + ").");
        }
        PoliticaDeContrasenas.validar(configurada);
        return configurada;
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
