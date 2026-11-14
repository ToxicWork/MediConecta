package ar.edu.mediconecta.api;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.api.dto.ErrorApi;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.TransicionInvalidaException;
import ar.edu.mediconecta.modelo.Turno;
import jakarta.ejb.EJBAccessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Qué código HTTP devuelve la API ante cada error; en particular, 409 ante una transición inválida (State). */
class MapeadorDeExcepcionesTest {

    @Test
    void unaTransicionInvalidaDelTurnoEs409() {
        Turno disponible = Fixtures.turno(LocalDateTime.now().plusDays(1));
        TransicionInvalidaException transicion = assertThrows(TransicionInvalidaException.class, disponible::cancelar);

        ErrorApi error = MapeadorDeExcepciones.clasificar(transicion, true);

        assertEquals(409, error.estado());
        assertEquals("transicion_invalida", error.error());
        assertEquals("No se puede cancelar un turno disponible.", error.mensaje());
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
