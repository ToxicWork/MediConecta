package ar.edu.mediconecta.mensajeria;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.modelo.Turno;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Builder del evento y selector de Facturación (Selective Consumer). */
class EventoTurnoTest {

    @Test
    void elBuilderGeneraSiempreUnIdUnicoYExigeLoObligatorio() {
        Turno turno = Fixtures.turno(LocalDateTime.now().plusDays(1));
        turno.reservar(Fixtures.paciente(7, "OSDE"), LocalDateTime.now().plusMinutes(5));
        EventoTurno a = EventoTurno.builder().tipo(TipoEventoTurno.TURNO_CONFIRMADO).turno(turno).build();
        EventoTurno b = EventoTurno.builder().tipo(TipoEventoTurno.TURNO_CONFIRMADO).turno(turno).build();
        assertNotNull(a.idEvento());
        assertNotEquals(a.idEvento(), b.idEvento());
        assertEquals("PRESENCIAL", a.modalidad());
        assertThrows(NullPointerException.class, () -> EventoTurno.builder().turno(turno).build());
        assertThrows(NullPointerException.class, () -> EventoTurno.builder().tipo(TipoEventoTurno.TURNO_CANCELADO)
                .turno(Fixtures.turno(LocalDateTime.now())).build(), "un turno sin paciente no genera evento");
    }

    @Test
    void facturacionSoloRecibeAutorizacionesYCancelaciones() {
        assertTrue(Suscripciones.recibe(Suscripciones.FACTURACION, "TURNO_AUTORIZADO"));
        assertTrue(Suscripciones.recibe(Suscripciones.FACTURACION, "TURNO_CANCELADO"));
        assertFalse(Suscripciones.recibe(Suscripciones.FACTURACION, "TURNO_CONFIRMADO"));
        assertFalse(Suscripciones.recibe(Suscripciones.FACTURACION, "AUTORIZACION_RECHAZADA"));
        assertTrue(Suscripciones.recibe(Suscripciones.NOTIFICACIONES, "TURNO_CONFIRMADO"));
    }
}
