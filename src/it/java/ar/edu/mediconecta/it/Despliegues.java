package ar.edu.mediconecta.it;

import ar.edu.mediconecta.mensajeria.EventoDuplicadoException;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.seguridad.CifradoConverter;
import ar.edu.mediconecta.seguridad.CifradorDeColumnas;
import ar.edu.mediconecta.seguridad.ClaveDeCifrado;
import ar.edu.mediconecta.seguridad.TokenRevocado;
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
