package ar.edu.mediconecta.negocio.copago;

import ar.edu.mediconecta.integracion.obrasocial.OrigenCobertura;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.integracion.obrasocial.TipoCopago;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Patrón Strategy: cada regla de copago y la elección de la estrategia según la cobertura. */
class CalculoCopagoTest {

    private static final BigDecimal PRECIO = new BigDecimal("32000.00");
    private static final List<CalculoCopago> ESTRATEGIAS = List.of(new CopagoPorcentual(), new CopagoMontoFijo(),
            new CopagoExento(), new TarifaParticular(), new CoberturaPendiente());

    private static ResultadoCobertura cubierta(TipoCopago tipo, String valor) {
        return new ResultadoCobertura(EstadoCobertura.CUBIERTA, tipo, valor == null ? null : new BigDecimal(valor), "210",
                true, "ok", OrigenCobertura.LEGADO, "1");
    }

    private static CalculoCopago elegir(ResultadoCobertura cobertura) {
        List<CalculoCopago> aplicables = ESTRATEGIAS.stream().filter(e -> e.aplica(cobertura)).toList();
        assertEquals(1, aplicables.size(), "las condiciones de las estrategias deben ser excluyentes");
        return aplicables.get(0);
    }

    @Test
    void porcentual() {
        ImporteTurno importe = elegir(cubierta(TipoCopago.PORCENTAJE, "30")).calcular(PRECIO, cubierta(TipoCopago.PORCENTAJE, "30"));
        assertEquals(new BigDecimal("9600.00"), importe.getImportePaciente());
        assertEquals(new BigDecimal("22400.00"), importe.getImporteObraSocial());
        assertTrue(importe.isRequierePago());
    }

    @Test
    void montoFijoNuncaSuperaElPrecio() {
        ResultadoCobertura fijo = cubierta(TipoCopago.MONTO_FIJO, "3500");
        assertEquals(new BigDecimal("3500.00"), elegir(fijo).calcular(PRECIO, fijo).getImportePaciente());
        assertEquals(new BigDecimal("1000.00"), elegir(fijo).calcular(new BigDecimal("1000.00"), fijo).getImportePaciente());
    }

    @Test
    void exentoNoCobraAlPaciente() {
        ResultadoCobertura exento = cubierta(TipoCopago.EXENTO, "0");
        ImporteTurno importe = elegir(exento).calcular(PRECIO, exento);
        assertFalse(importe.isRequierePago());
        assertEquals(PRECIO, importe.getImporteObraSocial());
    }

    @Test
    void particularPagaElTotal() {
        ResultadoCobertura particular = ResultadoCobertura.sinObraSocial();
        ImporteTurno importe = elegir(particular).calcular(PRECIO, particular);
        assertEquals(PRECIO, importe.getImportePaciente());
        assertEquals(0, importe.getImporteObraSocial().signum());
    }

    @Test
    void coberturaPendienteNoCobraNada() {
        ResultadoCobertura pendiente = ResultadoCobertura.pendiente("sin respuesta");
        ImporteTurno importe = elegir(pendiente).calcular(PRECIO, pendiente);
        assertFalse(importe.isRequierePago());
        assertTrue(pendiente.isRequiereAutorizacion(), "la cola de autorizaciones resolverá la cobertura");
    }
}
