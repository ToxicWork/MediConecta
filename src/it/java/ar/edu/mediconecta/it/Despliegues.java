package ar.edu.mediconecta.it;

import ar.edu.mediconecta.mensajeria.EventoDuplicadoException;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.seguridad.CifradoConverter;
import ar.edu.mediconecta.seguridad.CifradorDeColumnas;
import ar.edu.mediconecta.seguridad.ClaveDeCifrado;
import ar.edu.mediconecta.seguridad.TokenRevocado;
import org.jboss.shrinkwrap.api.Filters;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.WebArchive;

/**
 * El despliegue de cada prueba de integración: un WAR chico con el modelo, los repositorios (DAO) y el cifrado de
 * columnas, más la prueba. Usa la misma base que la aplicación (java:/mediconectaDS) y el mismo broker, sin tocar el
 * esquema. No incluye pantallas, servicios ni consumidores: esos siguen siendo los de mediconecta.war, que ya está
 * desplegado, y las pruebas de mensajería los ejercitan desde afuera, mandando el mensaje real al broker.
 */
final class Despliegues {

    private static final String BEANS = "<beans xmlns=\"https://jakarta.ee/xml/ns/jakartaee\" version=\"4.0\" "
            + "bean-discovery-mode=\"annotated\"/>";

    private Despliegues() {
    }

    /** Clases de la aplicación que no van en el despliegue de prueba: ya están en mediconecta.war, desplegada. */
    private static final String[] FUERA_DE_LA_PRUEBA = {
            // los consumidores: duplicarían las suscripciones durables (el mismo clientId)
            "mensajeria.AuditoriaSubscriber", "mensajeria.AutorizacionConsumer", "mensajeria.FacturacionSubscriber",
            "mensajeria.NotificacionesSubscriber",
            // lo que corre solo al desplegar o con un temporizador
            "negocio.CifradoDeDatosExistentes", "negocio.InicializadorDeAdministrador", "negocio.LiberadorDeHolds",
            "negocio.LimpiezaDePedidosIdempotentes", "negocio.ReintentadorDeReintegros", "negocio.VencedorDeDisponibilidad",
            "dao.ProteccionDeTablas",
            // la autenticación web y la de la API
            "seguridad.CabecerasDeSeguridad", "seguridad.ConfiguracionSeguridad", "seguridad.MecanismoAutenticacion",
            "seguridad.RevocacionDeTokens", "seguridad.ServicioDeTokens"};

    /**
     * La aplicación entera salvo las pantallas, la API, los consumidores y lo que corre solo (ver
     * {@link #FUERA_DE_LA_PRUEBA}), más las clases de la prueba: para ejercitar los procesos de negocio reales, con la
     * base, el broker y los sistemas externos del servidor. Las clases de las pruebas unitarias no entran.
     */
    static WebArchive conLaAplicacion(String nombre, Class<?>... pruebas) throws ClassNotFoundException {
        WebArchive war = ShrinkWrap.create(WebArchive.class, nombre + ".war")
                .addPackages(true, Filters.exclude(".*(Test|Fixtures|Inyectar|ParaPruebas)(\\$.*)?\\.class|.*/it/.*"),
                        "ar.edu.mediconecta")
                .deletePackages(true, "ar.edu.mediconecta.web", "ar.edu.mediconecta.api");
        for (String clase : FUERA_DE_LA_PRUEBA) {
            war.deleteClass(Class.forName("ar.edu.mediconecta." + clase));
        }
        return war.addClasses(BaseDePruebas.class, Despliegues.class)
                .addClasses(pruebas)
                .addAsResource("persistence-it.xml", "META-INF/persistence.xml")
                .addAsResource("wsdl/CoberturaService.wsdl") // el cliente SOAP de la obra social lo busca en el classpath
                .addAsWebInfResource(new StringAsset(BEANS), "beans.xml");
    }

    static WebArchive conModeloYRepositorios(String nombre, Class<?>... pruebas) {
        return ShrinkWrap.create(WebArchive.class, nombre + ".war")
                .addPackages(false, "ar.edu.mediconecta.modelo", "ar.edu.mediconecta.dao")
                .addClasses(CifradoConverter.class, ClaveDeCifrado.class, CifradorDeColumnas.class,
                        CifradorDeColumnas.DatoCifradoInvalidoException.class, TokenRevocado.class,
                        EventoDuplicadoException.class, EventoTurno.class, EventoTurno.Builder.class, TipoEventoTurno.class,
                        BaseDePruebas.class, Despliegues.class)
                .addClasses(pruebas)
                .addAsResource("persistence-it.xml", "META-INF/persistence.xml")
                .addAsWebInfResource(new StringAsset(BEANS), "beans.xml");
    }
}
