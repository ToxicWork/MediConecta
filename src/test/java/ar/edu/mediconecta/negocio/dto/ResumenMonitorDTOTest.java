package ar.edu.mediconecta.negocio.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** El resumen arriba del Monitor de eventos: cuántos procesó cada suscriptor y con qué demora. */
class ResumenMonitorDTOTest {

    @Test
    void cuentaSoloLosSuscriptoresALosQueLesCorrespondeElEvento() {
        FilaMonitorDTO primero = new FilaMonitorDTO("evento-1", "TURNO_CONFIRMADO", 1L, null, null, List.of(
                new EstadoSuscriptorDTO("notificaciones", true, true, 100L),
                new EstadoSuscriptorDTO("auditoria", true, true, 300L),
                new EstadoSuscriptorDTO("facturacion", false, false, null)));
        FilaMonitorDTO segundo = new FilaMonitorDTO("evento-2", "TURNO_CANCELADO", 2L, null, null, List.of(
                new EstadoSuscriptorDTO("notificaciones", true, true, 200L),
                new EstadoSuscriptorDTO("auditoria", true, false, null),
                new EstadoSuscriptorDTO("facturacion", true, false, null)));

        ResumenMonitorDTO resumen = ResumenMonitorDTO.de(List.of(primero, segundo));

        assertEquals(2, resumen.getPublicados());
        assertEquals(3, resumen.getProcesados());
        assertEquals(2, resumen.getPendientes());
        assertEquals(200L, resumen.getLatenciaPromedioMs());
        assertEquals(300L, resumen.getLatenciaMaximaMs());
    }

    @Test
    void sinEventosNoHayLatencias() {
        ResumenMonitorDTO resumen = ResumenMonitorDTO.de(List.of());

        assertEquals(0, resumen.getPublicados());
        assertEquals(0, resumen.getPendientes());
        assertNull(resumen.getLatenciaPromedioMs());
        assertNull(resumen.getLatenciaMaximaMs());
    }
}
