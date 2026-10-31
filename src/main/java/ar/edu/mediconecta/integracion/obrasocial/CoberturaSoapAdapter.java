package ar.edu.mediconecta.integracion.obrasocial;

import ar.edu.mediconecta.integracion.ConfiguracionIntegraciones;
import ar.edu.mediconecta.integracion.RegistroIntegraciones;
import ar.edu.mediconecta.integracion.SistemaExterno;
import ar.edu.mediconecta.integracion.obrasocial.soap.CoberturaPortType;
import ar.edu.mediconecta.integracion.obrasocial.soap.CoberturaService;
import ar.edu.mediconecta.integracion.obrasocial.soap.ConsultaCobertura;
import ar.edu.mediconecta.integracion.obrasocial.soap.EstadoAutorizacion;
import ar.edu.mediconecta.integracion.obrasocial.soap.EstadoCobertura;
import ar.edu.mediconecta.integracion.obrasocial.soap.SolicitudAutorizacion;
import ar.edu.mediconecta.integracion.resiliencia.CircuitBreaker;
import ar.edu.mediconecta.integracion.resiliencia.CircuitoAbiertoException;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.integracion.resiliencia.Reintento;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Prestacion;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.handler.Handler;
import jakarta.xml.ws.soap.SOAPFaultException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter del patrón GoF (Adaptee: el puerto JAX-WS generado por wsimport desde el WSDL del legado).
 * Traduce entre el dominio y los tipos SOAP y concentra la resiliencia frente a un sistema que no
 * controlamos:
 * <ul>
 *   <li>Timeout: 2 s para conectar y 3 s para responder.</li>
 *   <li>Retry: un reintento a los 500 ms, sólo en validarCobertura (es una consulta sin efectos).</li>
 *   <li>Circuit Breaker: 3 fallas seguidas abren el circuito 30 s.</li>
 *   <li>Fallback con Cache-Aside: la última validación exitosa (vigencia 24 h) o, si no hay, PENDIENTE.</li>
 * </ul>
 */
@ApplicationScoped
public class CoberturaSoapAdapter implements CoberturaGateway {

    private static final Duration VIGENCIA_CACHE = Duration.ofHours(24);
    private static final int TIMEOUT_CONEXION_MS = 2_000;
    private static final int TIMEOUT_RESPUESTA_MS = 3_000;
    private static final DateTimeFormatter FECHA_LEGADO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Inject
    private ConfiguracionIntegraciones config;

    @Inject
    private RegistroIntegraciones registro;

    private final CircuitBreaker circuito = new CircuitBreaker("Obra social (SOAP)", 3, Duration.ofSeconds(30));
    private final Map<String, EntradaCache> cache = new ConcurrentHashMap<>();
    private volatile CoberturaService servicio;

    @Override
    public ResultadoCobertura validarCobertura(Paciente paciente, Prestacion prestacion, LocalDateTime fecha,
                                               String referencia) {
        String clave = paciente.getId() + "|" + prestacion.getCodigo();
        long inicio = System.nanoTime();
        try {
            ResultadoCobertura resultado = Reintento.ejecutar(2, Duration.ofMillis(500),
                    () -> circuito.ejecutar(() -> invocarValidarCobertura(paciente, prestacion, fecha)));
            cache.put(clave, new EntradaCache(resultado, Instant.now()));
            registro.registrar(SistemaExterno.OBRA_SOCIAL, "validarCobertura", referencia, RegistroIntegraciones.OK,
                    resultado.resumen(), milis(inicio));
            return resultado;
        } catch (IntegracionNoDisponibleException falla) {
            String causa = (falla instanceof CircuitoAbiertoException ? "circuito abierto" : "sin respuesta")
                    + ": " + falla.getMessage();
            Optional<ResultadoCobertura> enCache = vigente(clave);
            if (enCache.isPresent()) {
                registro.registrar(SistemaExterno.OBRA_SOCIAL, "validarCobertura", referencia, RegistroIntegraciones.CACHE,
                        causa + " -> fallback con la última validación (" + enCache.get().resumen() + ")", milis(inicio));
                return enCache.get().desdeCache();
            }
            registro.registrar(SistemaExterno.OBRA_SOCIAL, "validarCobertura", referencia, RegistroIntegraciones.PENDIENTE,
                    causa + " -> fallback: cobertura pendiente", milis(inicio));
            return ResultadoCobertura.pendiente(falla instanceof CircuitoAbiertoException
                    ? "circuito abierto" : "sin respuesta");
        }
    }

    @Override
    public ResultadoAutorizacion autorizar(PedidoAutorizacion pedido) {
        long inicio = System.nanoTime();
        try {
            ResultadoAutorizacion resultado = circuito.ejecutar(() -> invocarSolicitarAutorizacion(pedido));
            registro.registrar(SistemaExterno.OBRA_SOCIAL, "solicitarAutorizacion", pedido.idSolicitud(),
                    RegistroIntegraciones.OK, (resultado.autorizada() ? "AUTORIZADA " + resultado.codigoAutorizacion()
                            : "RECHAZADA: " + resultado.motivo()), milis(inicio));
            return resultado;
        } catch (IntegracionNoDisponibleException falla) {
            registro.registrar(SistemaExterno.OBRA_SOCIAL, "solicitarAutorizacion", pedido.idSolicitud(),
                    falla instanceof CircuitoAbiertoException ? RegistroIntegraciones.CIRCUITO_ABIERTO : RegistroIntegraciones.ERROR,
                    falla.getMessage(), milis(inicio));
            throw falla;
        }
    }

    @Override
    public EstadoCircuito estadoCircuito() {
        return circuito.instantanea();
    }

    // ---- Traducción dominio <-> SOAP --------------------------------------------------------

    private ResultadoCobertura invocarValidarCobertura(Paciente paciente, Prestacion prestacion, LocalDateTime fecha) {
        ConsultaCobertura consulta = new ConsultaCobertura();
        consulta.setObraSocial(paciente.getObraSocial());
        consulta.setNroAfiliado(paciente.getNroAfiliado());
        consulta.setCodigoPrestacion(prestacion.getCodigo());
        consulta.setFechaPrestacion(fecha == null ? null : FECHA_LEGADO.format(fecha));
        var respuesta = invocar(() -> puerto().validarCobertura(consulta));
        boolean cubierto = respuesta.getEstado() == EstadoCobertura.CUBIERTO;
        return new ResultadoCobertura(
                cubierto ? ar.edu.mediconecta.modelo.EstadoCobertura.CUBIERTA : ar.edu.mediconecta.modelo.EstadoCobertura.PARTICULAR,
                cubierto ? TipoCopago.valueOf(respuesta.getTipoCopago().name()) : TipoCopago.NINGUNO,
                respuesta.getValorCopago(), respuesta.getPlan(), cubierto && respuesta.isRequiereAutorizacion(),
                respuesta.getMensaje(), OrigenCobertura.LEGADO, respuesta.getNroTransaccion());
    }

    private ResultadoAutorizacion invocarSolicitarAutorizacion(PedidoAutorizacion pedido) {
        SolicitudAutorizacion solicitud = new SolicitudAutorizacion();
        solicitud.setIdSolicitud(pedido.idSolicitud());
        solicitud.setObraSocial(pedido.obraSocial());
        solicitud.setPlan(pedido.plan());
        solicitud.setNroAfiliado(pedido.nroAfiliado());
        solicitud.setCodigoPrestacion(pedido.codigoPrestacion());
        solicitud.setFechaPrestacion(pedido.fechaPrestacion() == null ? null : FECHA_LEGADO.format(pedido.fechaPrestacion()));
        solicitud.setMatriculaPrestador(pedido.matriculaPrestador());
        var respuesta = invocar(() -> puerto().solicitarAutorizacion(solicitud));
        return new ResultadoAutorizacion(respuesta.getEstado() == EstadoAutorizacion.AUTORIZADA,
                respuesta.getCodigoAutorizacion(), respuesta.getMotivo(), respuesta.getNroTransaccion());
    }

    /** Toda falla de JAX-WS (timeout, conexión rechazada, SOAP Fault) es una falla técnica para el circuito. */
    private static <T> T invocar(java.util.function.Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (SOAPFaultException fault) {
            boolean credenciales = fault.getFault() != null && fault.getFault().getFaultCode() != null
                    && fault.getFault().getFaultCode().contains("FailedAuthentication");
            throw new IntegracionNoDisponibleException("SOAP Fault del legado: " + fault.getMessage(), !credenciales, fault);
        } catch (WebServiceException sinRespuesta) {
            throw new IntegracionNoDisponibleException(descripcion(sinRespuesta), true, sinRespuesta);
        }
    }

    private static String descripcion(Throwable error) {
        Throwable raiz = error;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String nombre = raiz.getClass().getSimpleName();
        if (nombre.contains("Timeout")) {
            return "timeout: el legado no respondió a tiempo";
        }
        if (nombre.contains("Connect")) {
            return "no se pudo conectar con el legado";
        }
        return nombre + (raiz.getMessage() == null ? "" : ": " + raiz.getMessage());
    }

    /**
     * El Service (que parsea el WSDL local) se crea una sola vez; el puerto, que no es thread-safe para su
     * contexto de pedido, se crea por llamada con la dirección, los timeouts y el handler WS-Security.
     */
    private CoberturaPortType puerto() {
        if (servicio == null) {
            synchronized (this) {
                if (servicio == null) {
                    servicio = new CoberturaService();
                }
            }
        }
        CoberturaPortType puerto = servicio.getCoberturaPort();
        BindingProvider proveedor = (BindingProvider) puerto;
        Map<String, Object> contexto = proveedor.getRequestContext();
        contexto.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, config.urlObraSocial());
        for (String prefijo : List.of("jakarta", "javax")) {
            contexto.put(prefijo + ".xml.ws.client.connectionTimeout", TIMEOUT_CONEXION_MS);
            contexto.put(prefijo + ".xml.ws.client.receiveTimeout", TIMEOUT_RESPUESTA_MS);
        }
        @SuppressWarnings("rawtypes")
        List<Handler> cadena = new ArrayList<>(proveedor.getBinding().getHandlerChain());
        cadena.add(new UsernameTokenClientHandler(config.usuarioObraSocial(), config.claveObraSocial()));
        proveedor.getBinding().setHandlerChain(cadena);
        return puerto;
    }

    private Optional<ResultadoCobertura> vigente(String clave) {
        EntradaCache entrada = cache.get(clave);
        if (entrada == null || entrada.guardadaEn().plus(VIGENCIA_CACHE).isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entrada.resultado());
    }

    private static long milis(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    private record EntradaCache(ResultadoCobertura resultado, Instant guardadaEn) {
    }
}
