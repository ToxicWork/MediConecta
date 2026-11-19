package ar.edu.mediconecta.api;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.TransicionesInvalidas;
import ar.edu.mediconecta.api.dto.ProblemaApi;
import ar.edu.mediconecta.modelo.DatosConfirmacion;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Rol;
import ar.edu.mediconecta.modelo.TransicionInvalidaException;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.excepciones.AccesoDenegadoException;
import ar.edu.mediconecta.negocio.excepciones.ClaveDeIdempotenciaReutilizadaException;
import ar.edu.mediconecta.negocio.excepciones.ParametroInvalidoException;
import ar.edu.mediconecta.negocio.excepciones.PrecondicionFallidaException;
import ar.edu.mediconecta.negocio.excepciones.TurnoInexistenteException;
import jakarta.ejb.EJBAccessException;
import jakarta.json.bind.JsonbException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Qué problema (RFC 9457) y qué código HTTP devuelve la API ante cada error; en particular, 409 ante una transición
 * inválida (State).
 */
class MapeadorDeExcepcionesTest {

    private static final String RUTA = "/mediconecta/api/v1/turnos/5/cancelacion";

    @Test
    void unaTransicionInvalidaDelTurnoEs409() {
        Turno disponible = Fixtures.turno(LocalDateTime.now().plusDays(1));
        TransicionInvalidaException transicion = assertThrows(TransicionInvalidaException.class,
                () -> disponible.cancelar(Rol.PACIENTE, LocalDateTime.now()));

        ProblemaApi problema = MapeadorDeExcepciones.clasificar(transicion, true, RUTA);

        assertEquals(409, problema.status());
        assertEquals(TipoDeProblema.TRANSICION_INVALIDA.uri(), problema.type());
        assertEquals("Transición de estado inválida", problema.title());
        assertEquals("No se puede cancelar un turno disponible.", problema.detail());
        assertEquals(RUTA, problema.instance());
    }

    /** Toda transición que el State no admite (las doce) se responde con 409 y su mensaje como detalle. */
    @ParameterizedTest(name = "{0} + {1} → 409")
    @MethodSource("ar.edu.mediconecta.TransicionesInvalidas#casos")
    void todaTransicionInvalidaDelStateEs409(EstadoTurno estado, String operacion, Consumer<Turno> aplicar, String mensaje) {
        Turno turno = TransicionesInvalidas.enEstado(estado);
        TransicionInvalidaException transicion = assertThrows(TransicionInvalidaException.class, () -> aplicar.accept(turno));

        ProblemaApi problema = MapeadorDeExcepciones.clasificar(transicion, true, RUTA);

        assertEquals(409, problema.status());
        assertEquals(TipoDeProblema.TRANSICION_INVALIDA.uri(), problema.type());
        assertEquals(mensaje, problema.detail());
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

        ProblemaApi problema = MapeadorDeExcepciones.clasificar(yaEmpezo, true, RUTA);

        assertEquals(409, problema.status());
        assertEquals("El turno ya empezó: no se puede cancelar.", problema.detail());
    }

    /** Los campos del formato anterior (docs/api/README.md) siguen en la respuesta, como extensiones. */
    @Test
    void losCamposDelFormatoAnteriorSiguenEnElProblema() {
        ProblemaApi problema = MapeadorDeExcepciones.clasificar(new ErrorDeNegocioException("El turno ya fue tomado."), true, RUTA);

        assertEquals(problema.status(), problema.estado());
        assertEquals(problema.detail(), problema.mensaje());
        assertEquals("regla_de_negocio", problema.error());
        assertNotNull(problema.fecha());
    }

    @Test
    void unTurnoInexistenteOAjenoEs404ComoUnIdQueNoExiste() {
        ProblemaApi problema = MapeadorDeExcepciones.clasificar(new TurnoInexistenteException(), true, RUTA);

        assertEquals(404, problema.status());
        assertEquals("no_encontrado", problema.error());
        assertEquals(MapeadorDeExcepciones.deEstadoHttp(404, RUTA).detail(), problema.detail());
    }

    @Test
    void otraReglaDeNegocioTambienEs409PeroConOtroCodigo() {
        ProblemaApi problema = MapeadorDeExcepciones.clasificar(new ErrorDeNegocioException("El turno ya fue tomado."), true, RUTA);

        assertEquals(409, problema.status());
        assertEquals("regla_de_negocio", problema.error());
    }

    @Test
    void unAccesoDenegadoEs403() {
        assertEquals(403, MapeadorDeExcepciones.clasificar(new AccesoDenegadoException("No es de tu agenda."), true, RUTA).status());
    }

    @Test
    void unEtagDesactualizadoEs412YUnaClaveReutilizadaEs422() {
        assertEquals(412, MapeadorDeExcepciones.clasificar(new PrecondicionFallidaException(), true, RUTA).status());
        ProblemaApi reutilizada = MapeadorDeExcepciones.clasificar(new ClaveDeIdempotenciaReutilizadaException(), true, RUTA);
        assertEquals(422, reutilizada.status());
        assertEquals("clave_reutilizada", reutilizada.error());
        assertEquals(400, MapeadorDeExcepciones.clasificar(new ParametroInvalidoException("x"), true, RUTA).status());
    }

    @Test
    void sinPermisoEs401SiNoHayTokenY403SiElRolNoAlcanza() {
        assertEquals(401, MapeadorDeExcepciones.clasificar(new EJBAccessException("x"), false, RUTA).status());
        assertEquals(403, MapeadorDeExcepciones.clasificar(new EJBAccessException("x"), true, RUTA).status());
    }

    @Test
    void unCuerpoQueNoEsJsonEs400SinElMensajeDelParser() {
        ProblemaApi problema = MapeadorDeExcepciones.clasificar(
                new JsonbException("Unexpected char 123 at (line no=1, column no=2) in ar.edu.mediconecta.api.dto"), false, RUTA);

        assertEquals(400, problema.status());
        assertEquals("pedido_invalido", problema.error());
        assertFalse(problema.detail().contains("line no"));
        assertFalse(problema.detail().contains("ar.edu"));
    }

    @Test
    void losErroresDelPropioJaxRsConservanSuCodigoConUnDetalleFijo() {
        assertEquals("metodo_no_permitido", MapeadorDeExcepciones.deEstadoHttp(405, RUTA).error());
        assertEquals("no_aceptable", MapeadorDeExcepciones.deEstadoHttp(406, RUTA).error());
        assertEquals("tipo_no_soportado", MapeadorDeExcepciones.deEstadoHttp(415, RUTA).error());
        ProblemaApi otro = MapeadorDeExcepciones.deEstadoHttp(410, RUTA);
        assertEquals(410, otro.status());
        assertEquals("error_http", otro.error());
        assertEquals(503, MapeadorDeExcepciones.deEstadoHttp(503, RUTA).status());
        assertEquals("error_interno", MapeadorDeExcepciones.deEstadoHttp(503, RUTA).error());
    }

    @Test
    void unErrorInesperadoEs500YNoFiltraDetallesInternos() {
        ProblemaApi problema = MapeadorDeExcepciones.clasificar(
                new IllegalStateException("ORA-00942 tabla usuario; ar.edu.mediconecta.dao.TurnoDAO"), true, RUTA);

        assertEquals(500, problema.status());
        assertFalse(problema.detail().contains("usuario"));
        assertFalse(problema.detail().contains("TurnoDAO"));
        assertFalse(problema.title().contains("TurnoDAO"));
    }
}
