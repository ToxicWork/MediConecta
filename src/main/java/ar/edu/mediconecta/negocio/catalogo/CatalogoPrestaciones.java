package ar.edu.mediconecta.negocio.catalogo;

import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Prestacion;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Lock;
import jakarta.ejb.LockType;
import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Singleton (EJB): catálogo de prestaciones y precios, consultado en cada cotización y casi inmutable.
 * El contenedor garantiza una sola instancia por aplicación, lo carga al desplegar ({@code @Startup}),
 * permite lecturas concurrentes ({@code LockType.READ}) y lo recarga de madrugada con lock exclusivo.
 * Los precios viven en {@code prestaciones.properties}: se actualizan sin recompilar.
 */
@Singleton
@Startup
@Lock(LockType.READ)
public class CatalogoPrestaciones {

    private static final Logger LOG = Logger.getLogger(CatalogoPrestaciones.class.getName());

    private Properties tabla = new Properties();

    @PostConstruct
    void cargar() {
        Properties nueva = new Properties();
        try (InputStream entrada = CatalogoPrestaciones.class.getResourceAsStream("/prestaciones.properties")) {
            if (entrada != null) {
                nueva.load(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            LOG.warning("No se pudo leer prestaciones.properties; se usan los valores por defecto.");
        }
        tabla = nueva;
        LOG.info("Catálogo de prestaciones cargado (" + nueva.size() + " entradas).");
    }

    @Lock(LockType.WRITE)
    @Schedule(hour = "3", minute = "0", persistent = false)
    void recargar() {
        cargar();
    }

    public Prestacion para(Turno turno) {
        return para(turno.getProfesional().getEspecialidad(), turno.getModalidad());
    }

    public Prestacion para(String especialidad, Modalidad modalidad) {
        String nombre = especialidad == null || especialidad.isBlank() ? "Clínica médica" : especialidad.trim();
        BigDecimal base = new BigDecimal(tabla.getProperty("precio." + normalizar(nombre),
                tabla.getProperty("precio.base", "25000")));
        if (modalidad == Modalidad.TELEMEDICINA) {
            BigDecimal descuento = new BigDecimal(tabla.getProperty("telemedicina.descuento", "0.20"));
            BigDecimal precio = base.multiply(BigDecimal.ONE.subtract(descuento)).setScale(2, RoundingMode.HALF_UP);
            return new Prestacion(tabla.getProperty("codigo.telemedicina", "420199"), "Teleconsulta de " + nombre, precio);
        }
        return new Prestacion(tabla.getProperty("codigo.presencial", "420101"), "Consulta de " + nombre,
                base.setScale(2, RoundingMode.HALF_UP));
    }

    static String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }
}
