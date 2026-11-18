package ar.edu.mediconecta.api;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.api.dto.ErrorApi;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.TransicionInvalidaException;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.excepciones.TurnoInexistenteException;
import jakarta.ejb.EJBAccessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qué código HTTP devuelve la API ante cada error; en particular, 409 ante una transición inválida (State). */
class MapeadorDeExcepcionesTest {

    @Test
    void unaTransicionInvalidaDelTurnoEs409() {
        Turno disponible = Fixtures.turno(LocalDateTime.now().plusDays(1));
        TransicionInvalidaException transicion = assertThrows(TransicionInvalidaException.class,
                () -> disponible.cancelar(Rol.PACIENTE, LocalDateTime.now()));

        ErrorApi error = MapeadorDeExcepciones.clasificar(transicion, true);

        assertEquals(409, error.estado());
        assertEquals("transicion_invalida", error.error());
        assertEquals("No se puede cancelar un turno disponible.", error.mensaje());
    }

    /** D-13: cancelar por la API un turno que ya empezó también es una transición inválida: 409, no 200. */
    @Test
    void cancelarUnTurnoQueYaEmpezoEs409() {
        LocalDateTime hora = LocalDateTime.of(2026, 11, 17, 10, 0);
        Turno turno = Fixtures.turno(hora);
        turno.reservar(Fixtures.paciente(7, "OSDE"), hora.minusHours(1));
        turno.confirmar(new DatosConfirmacion(EstadoCobertura.CUBIERTA, "OSDE 210", "210", "420101",
                new BigDecimal("32000"), new BigDecimal("9600"), new BigDecimal("22400"), EstadoAutorizacion.PENDIENTE));
        TransicionInvalidaException yaEmpezo = assertThrows(TransicionInvalidaException.class,
                () -> turno.cancelar(Rol.PACIENTE, hora.plusMinutes(5)));

        ErrorApi error = MapeadorDeExcepciones.clasificar(yaEmpezo, true);

        assertEquals(409, error.estado());
        assertEquals("El turno ya empezó: no se puede cancelar.", error.mensaje());
    }

    @Test
    void unTurnoInexistenteOAjenoEs404ComoUnIdQueNoExiste() {
        ErrorApi error = MapeadorDeExcepciones.clasificar(new TurnoInexistenteException(), true);

        assertEquals(404, error.estado());
        assertEquals("no_encontrado", error.error());
    }

    @Test
    void otraReglaDeNegocioTambienEs409PeroConOtroCodigo() {
        ErrorApi error = MapeadorDeExcepciones.clasificar(new ErrorDeNegocioException("El turno ya fue tomado."), true);

        assertEquals(409, error.estado());
        assertEquals("regla_de_negocio", error.error());
    }

    @Test
    void sinPermisoEs401SiNoHayTokenY403SiElRolNoAlcanza() {
        assertEquals(401, MapeadorDeExcepciones.clasificar(new EJBAccessException("x"), false).estado());
        assertEquals(403, MapeadorDeExcepciones.clasificar(new EJBAccessException("x"), true).estado());
    }

    @Test
    void unErrorInesperadoEs500YNoFiltraDetallesInternos() {
        ErrorApi error = MapeadorDeExcepciones.clasificar(
                new IllegalStateException("ORA-00942 tabla usuario; ar.edu.mediconecta.dao.TurnoDAO"), true);

        assertEquals(500, error.estado());
        assertFalse(error.mensaje().contains("usuario"));
        assertFalse(error.mensaje().contains("TurnoDAO"));
    }
}
