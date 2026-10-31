package ar.edu.mediconecta.negocio.catalogo;

import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Prestacion;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogoPrestacionesTest {

    private final CatalogoPrestaciones catalogo = new CatalogoPrestaciones();

    {
        catalogo.cargar();
    }

    @Test
    void precioPorEspecialidadSinImportarAcentosNiMayusculas() {
        Prestacion cardiologia = catalogo.para("Cardiología", Modalidad.PRESENCIAL);
        assertEquals("420101", cardiologia.getCodigo());
        assertEquals(new BigDecimal("32000.00"), cardiologia.getPrecio());
        assertEquals(new BigDecimal("32000.00"), catalogo.para("CARDIOLOGIA", Modalidad.PRESENCIAL).getPrecio());
    }

    @Test
    void laTeleconsultaTieneDescuentoYOtroCodigo() {
        Prestacion tele = catalogo.para("Cardiologia", Modalidad.TELEMEDICINA);
        assertEquals("420199", tele.getCodigo());
        assertEquals(new BigDecimal("25600.00"), tele.getPrecio());
    }

    @Test
    void unaEspecialidadDesconocidaUsaElPrecioBase() {
        assertEquals(new BigDecimal("25000.00"), catalogo.para("Oftalmologo", Modalidad.PRESENCIAL).getPrecio());
        assertEquals("clinica_medica", CatalogoPrestaciones.normalizar(" Clínica Médica "));
    }
}
