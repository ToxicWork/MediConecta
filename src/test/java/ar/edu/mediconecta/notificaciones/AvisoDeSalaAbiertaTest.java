package ar.edu.mediconecta.notificaciones;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.mensajeria.Suscripciones;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.CanalNotificacion;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Notificacion;
import ar.edu.mediconecta.modelo.Turno;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-31: cuando el profesional abre la sala, al paciente le llega un aviso; al profesional y a Facturación, no. */
class AvisoDeSalaAbiertaTest {

    private static EventoTurno salaAbierta() {
        LocalDateTime hora = LocalDateTime.of(2026, 11, 20, 10, 0);
        Turno turno = new Turno(Fixtures.profesional(20, "Clínica"), hora, Modalidad.TELEMEDICINA);
        turno.reservar(Fixtures.paciente(7, "OSDE"), hora.minusHours(1));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE 210", "210", "420101",
                new BigDecimal("25600"), new BigDecimal("7680"), new BigDecimal("17920"), EstadoAutorizacion.PENDIENTE));
        turno.asignarSala("sala-1", "https://video/p", "https://video/q");
        return EventoTurno.builder().tipo(TipoEventoTurno.SALA_ABIERTA).turno(turno).build();
    }

    @Test
    void elPacienteRecibeElAvisoConElDiaYElCaminoParaEntrar() {
        EventoTurno evento = salaAbierta();
        Notificacion aviso = new ProcesadorEmail().crearNotificacion(evento);
        assertEquals(evento.pacienteId(), aviso.getDestinatarioId());
        assertEquals(CanalNotificacion.EMAIL, aviso.getCanal());
        assertEquals("Tu profesional ya está en la sala", aviso.getAsunto());
        assertTrue(aviso.getCuerpo().contains("20/11/2026 10:00"), aviso.getCuerpo());
        assertTrue(aviso.getCuerpo().contains("Unirme a la consulta"), aviso.getCuerpo());
    }

    @Test
    void alProfesionalNoLeLlegaAvisoPorqueLaAbrioEl() {
        assertNull(new ProcesadorAvisoProfesional().crearNotificacion(salaAbierta()));
    }

    @Test
    void facturacionNoRecibeElEvento() {
        assertFalse(Suscripciones.recibe(Suscripciones.FACTURACION, TipoEventoTurno.SALA_ABIERTA.name()));
        assertTrue(Suscripciones.recibe(Suscripciones.NOTIFICACIONES, TipoEventoTurno.SALA_ABIERTA.name()));
        assertTrue(Suscripciones.recibe(Suscripciones.AUDITORIA, TipoEventoTurno.SALA_ABIERTA.name()));
    }
}
