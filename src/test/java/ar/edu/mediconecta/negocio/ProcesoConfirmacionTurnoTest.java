package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.Fixtures;
import ar.edu.mediconecta.Inyectar;
import ar.edu.mediconecta.dao.SolicitudAutorizacionDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.integracion.obrasocial.OrigenCobertura;
import ar.edu.mediconecta.integracion.obrasocial.ResultadoCobertura;
import ar.edu.mediconecta.integracion.obrasocial.TipoCopago;
import ar.edu.mediconecta.mensajeria.ProductorSolicitudesAutorizacion;
import ar.edu.mediconecta.mensajeria.PublicadorEventosTurno;
import ar.edu.mediconecta.mensajeria.TipoEventoTurno;
import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.EstadoAutorizacion;
import ar.edu.mediconecta.modelo.EstadoCobertura;
import ar.edu.mediconecta.modelo.EstadoTurno;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.negocio.catalogo.CatalogoPrestaciones;
import ar.edu.mediconecta.negocio.copago.CalculoCopago;
import ar.edu.mediconecta.negocio.copago.CoberturaPendiente;
import ar.edu.mediconecta.negocio.copago.CopagoExento;
import ar.edu.mediconecta.negocio.copago.CopagoMontoFijo;
import ar.edu.mediconecta.negocio.copago.CopagoPorcentual;
import ar.edu.mediconecta.negocio.copago.SelectorCalculoCopago;
import ar.edu.mediconecta.negocio.copago.TarifaParticular;
import ar.edu.mediconecta.negocio.dto.CotizacionTurnoDTO;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import ar.edu.mediconecta.negocio.excepciones.PagoRequeridoException;
import ar.edu.mediconecta.negocio.excepciones.ReservaInvalidaException;
import ar.edu.mediconecta.negocio.validacion.CadenaDeValidacion;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El flujo crítico de la confirmación: validaciones (Chain of Responsibility), cobertura, copago (Strategy, con el
 * selector real), cobro, transición a CONFIRMADO (State), autorización por la cola y evento al tópico. Lo externo
 * (obra social, pasarela, JMS) está simulado.
 */
class ProcesoConfirmacionTurnoTest {

    private static final long TURNO = 10L;
    private static final long PACIENTE = 7L;

    private final TurnoDAO turnoDAO = mock(TurnoDAO.class);
    private final ServicioDeObrasSociales obrasSociales = mock(ServicioDeObrasSociales.class);
    private final ServicioDePagos pagos = mock(ServicioDePagos.class);
    private final SolicitudAutorizacionDAO solicitudes = mock(SolicitudAutorizacionDAO.class);
    private final ProductorSolicitudesAutorizacion cola = mock(ProductorSolicitudesAutorizacion.class);
    private final PublicadorEventosTurno publicador = mock(PublicadorEventosTurno.class);
    private final CatalogoPrestaciones catalogo = new CatalogoPrestaciones();
    private final Paciente paciente = Fixtures.paciente(PACIENTE, "OSDE");
    private ProcesoConfirmacionTurno proceso;
    private Turno turno;
    private BigDecimal precio;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void armar() {
        Inyectar.invocar(catalogo, "cargar");
        CadenaDeValidacion validaciones = Inyectar.en(new CadenaDeValidacion(), "turnoDAO", turnoDAO);
        Inyectar.invocar(validaciones, "armar");
        Instance<CalculoCopago> estrategias = mock(Instance.class);
        when(estrategias.stream()).thenAnswer(i -> Stream.of(new CopagoExento(), new CopagoMontoFijo(),
                new CopagoPorcentual(), new CoberturaPendiente(), new TarifaParticular()));
        SelectorCalculoCopago copagos = Inyectar.en(new SelectorCalculoCopago(), "estrategias", estrategias);
        Inyectar.en(copagos, "porDefecto", new TarifaParticular());

        proceso = new ProcesoConfirmacionTurno();
        Inyectar.en(proceso, "turnoDAO", turnoDAO);
        Inyectar.en(proceso, "validaciones", validaciones);
        Inyectar.en(proceso, "catalogo", catalogo);
        Inyectar.en(proceso, "obrasSociales", obrasSociales);
        Inyectar.en(proceso, "copagos", copagos);
        Inyectar.en(proceso, "pagos", pagos);
        Inyectar.en(proceso, "solicitudes", solicitudes);
        Inyectar.en(proceso, "colaAutorizaciones", cola);
        Inyectar.en(proceso, "publicador", publicador);

        turno = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(2)), TURNO);
        turno.reservar(paciente, LocalDateTime.now().plusMinutes(5));
        when(turnoDAO.buscarPorIdParaActualizar(TURNO)).thenReturn(turno);
        when(turnoDAO.buscarPorId(TURNO)).thenReturn(turno);
        precio = catalogo.para(turno).getPrecio();
    }

    private void conCobertura(ResultadoCobertura cobertura) {
        when(obrasSociales.validarCobertura(eq(paciente), any(), any(), eq("turno-" + TURNO))).thenReturn(cobertura);
    }

    @Test
    void unParticularPagaElTotalYSeConfirmaSinAutorizacion() {
        conCobertura(ResultadoCobertura.sinObraSocial());
        DatosPago datos = new DatosPago();

        assertSame(turno, proceso.confirmar(TURNO, PACIENTE, datos));

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        assertEquals(EstadoAutorizacion.NO_REQUIERE, turno.getEstadoAutorizacion());
        assertEquals(0, precio.compareTo(turno.getImportePaciente()));
        verify(pagos).cobrar(turno, turno.getImportePaciente(), datos);
        verify(solicitudes, never()).guardar(any());
        verify(publicador).publicar(argThat(e -> e.tipo() == TipoEventoTurno.TURNO_CONFIRMADO && e.turnoId() == TURNO));
    }

    @Test
    void sinDatosDePagoNoSeCobraNiSeConfirma() {
        conCobertura(ResultadoCobertura.sinObraSocial());

        assertThrows(PagoRequeridoException.class, () -> proceso.confirmar(TURNO, PACIENTE, null));

        assertEquals(EstadoTurno.HOLD, turno.getEstado());
        verify(pagos, never()).cobrar(any(), any(), any());
        verify(publicador, never()).publicar(any());
    }

    /** Copago porcentual con autorización previa: el paciente paga su parte y la autorización va por la cola. */
    @Test
    void conCoberturaQueRequiereAutorizacionSePideALaColaPuntoAPunto() {
        conCobertura(new ResultadoCobertura(EstadoCobertura.CUBIERTA, TipoCopago.PORCENTAJE, new BigDecimal("30"), "210",
                true, "Cubierta con copago", OrigenCobertura.LEGADO, "4711"));

        proceso.confirmar(TURNO, PACIENTE, new DatosPago());

        assertEquals(EstadoAutorizacion.PENDIENTE, turno.getEstadoAutorizacion());
        assertEquals(0, precio.multiply(new BigDecimal("0.30")).compareTo(turno.getImportePaciente()));
        assertEquals(0, precio.subtract(turno.getImportePaciente()).compareTo(turno.getImporteObraSocial()));
        verify(solicitudes).guardar(any());
        verify(cola).enviar(any());
    }

    /** Fallback: si la obra social no responde, el turno se confirma sin cobro y la cobertura se resuelve después. */
    @Test
    void conLaObraSocialSinResponderSeConfirmaSinCobrar() {
        conCobertura(ResultadoCobertura.pendiente("sin respuesta"));

        proceso.confirmar(TURNO, PACIENTE, null);

        assertEquals(EstadoTurno.CONFIRMADO, turno.getEstado());
        assertEquals(0, BigDecimal.ZERO.compareTo(turno.getImportePaciente()));
        verify(pagos, never()).cobrar(any(), any(), any());
    }

    @Test
    void elHoldDeOtroPacienteNoSeConfirma() {
        assertThrows(ReservaInvalidaException.class, () -> proceso.confirmar(TURNO, 8L, new DatosPago()));

        verify(pagos, never()).cobrar(any(), any(), any());
        assertEquals(EstadoTurno.HOLD, turno.getEstado());
    }


    @Test
    void cotizarDevuelveLaPrestacionLaCoberturaYElImporte() {
        ResultadoCobertura cobertura = ResultadoCobertura.sinObraSocial();
        conCobertura(cobertura);

        CotizacionTurnoDTO cotizacion = proceso.cotizar(TURNO);

        assertSame(cobertura, cotizacion.getCobertura());
        assertEquals(0, precio.compareTo(cotizacion.getImporte().getImportePaciente()));
        assertEquals(catalogo.para(turno).getCodigo(), cotizacion.getPrestacion().getCodigo());
    }

    @Test
    void noSeCotizaUnTurnoQueYaNoEstaRetenido() {
        Turno libre = Fixtures.conId(Fixtures.turno(LocalDateTime.now().plusDays(2)), 11L);
        when(turnoDAO.buscarPorId(11L)).thenReturn(libre);

        ReservaInvalidaException error = assertThrows(ReservaInvalidaException.class, () -> proceso.cotizar(11L));

        assertEquals("El turno ya no está retenido a tu nombre.", error.getMessage());
    }
}
