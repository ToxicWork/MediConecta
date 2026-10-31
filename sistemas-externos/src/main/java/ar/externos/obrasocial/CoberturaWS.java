package ar.externos.obrasocial;

import ar.externos.consola.EstadoSimuladores;
import jakarta.inject.Inject;
import jakarta.jws.HandlerChain;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Servicio SOAP del sistema legado de la obra social (simulado). El WSDL lo publica el propio
 * servidor en {@code /externos/CoberturaService?wsdl}; MediConecta guarda una copia en su repositorio
 * y genera el cliente con wsimport, como se hace con un tercero cuyo contrato no controlamos.
 *
 * <p>Toda invocación pasa antes por {@link ValidadorUsernameTokenHandler} (WS-Security UsernameToken),
 * declarado en {@code handlers.xml}.
 */
@WebService(serviceName = "CoberturaService", portName = "CoberturaPort", name = "CoberturaPortType",
        targetNamespace = CoberturaWS.NS)
@HandlerChain(file = "handlers.xml")
public class CoberturaWS {

    public static final String NS = "http://legado.obrasocial.ar/cobertura/v1";

    private static final Logger LOG = Logger.getLogger(CoberturaWS.class.getName());
    private static final AtomicLong TRANSACCIONES = new AtomicLong(100_000);

    /** El legado deduplica por idSolicitud: reenviar la misma solicitud devuelve la misma respuesta. */
    private static final Map<String, ResultadoAutorizacion> AUTORIZACIONES = new ConcurrentHashMap<>();

    @Inject
    private EstadoSimuladores estado;

    @WebMethod(operationName = "validarCobertura")
    @WebResult(name = "resultado")
    public ResultadoCobertura validarCobertura(@WebParam(name = "consulta") ConsultaCobertura consulta) {
        simularDisponibilidad("validarCobertura");
        ResultadoCobertura resultado = PadronAfiliados.consultar(nuevaTransaccion(), consulta);
        estado.registrar("Obra social (SOAP)", "validarCobertura " + consulta.getObraSocial() + " · "
                + consulta.getCodigoPrestacion(), resultado.getEstado() + " " + resultado.getTipoCopago());
        return resultado;
    }

    @WebMethod(operationName = "solicitarAutorizacion")
    @WebResult(name = "autorizacion")
    public ResultadoAutorizacion solicitarAutorizacion(@WebParam(name = "solicitud") SolicitudAutorizacion solicitud) {
        simularDisponibilidad("solicitarAutorizacion");
        ResultadoAutorizacion previa = AUTORIZACIONES.get(solicitud.getIdSolicitud());
        if (previa != null) {
            estado.registrar("Obra social (SOAP)", "solicitarAutorizacion " + solicitud.getIdSolicitud(),
                    "duplicada: se devuelve la respuesta original");
            return previa;
        }
        dormir(estado.getDemoraAutorizacionMs()); // la auditoría médica del legado no es instantánea

        ConsultaCobertura consulta = new ConsultaCobertura();
        consulta.setObraSocial(solicitud.getObraSocial());
        consulta.setPlan(solicitud.getPlan());
        consulta.setNroAfiliado(solicitud.getNroAfiliado());
        consulta.setCodigoPrestacion(solicitud.getCodigoPrestacion());
        String transaccion = nuevaTransaccion();
        ResultadoCobertura cobertura = PadronAfiliados.consultar(transaccion, consulta);

        ResultadoAutorizacion resultado;
        if (cobertura.getEstado() != EstadoCobertura.CUBIERTO) {
            resultado = new ResultadoAutorizacion(transaccion, solicitud.getIdSolicitud(), EstadoAutorizacion.RECHAZADA,
                    null, cobertura.getMensaje());
        } else if (estado.getAutorizaciones() == EstadoSimuladores.ModoAutorizacion.RECHAZAR) {
            resultado = new ResultadoAutorizacion(transaccion, solicitud.getIdSolicitud(), EstadoAutorizacion.RECHAZADA,
                    null, "La auditoría médica no autorizó la prestación.");
        } else {
            String codigo = "AUT-" + transaccion;
            resultado = new ResultadoAutorizacion(transaccion, solicitud.getIdSolicitud(), EstadoAutorizacion.AUTORIZADA,
                    codigo, "Prestación autorizada.");
        }
        AUTORIZACIONES.put(solicitud.getIdSolicitud(), resultado);
        estado.registrar("Obra social (SOAP)", "solicitarAutorizacion " + solicitud.getIdSolicitud(),
                resultado.getEstado() + (resultado.getCodigoAutorizacion() == null ? "" : " " + resultado.getCodigoAutorizacion()));
        return resultado;
    }

    /** CAIDO: responde con un SOAP Fault. LENTO: tarda más que el timeout del cliente. */
    private void simularDisponibilidad(String operacion) {
        switch (estado.getObraSocial()) {
            case CAIDO -> {
                estado.registrar("Obra social (SOAP)", operacion, "SOAP Fault: servicio no disponible");
                LOG.warning("[Legado obra social] " + operacion + " rechazada: el sistema está caído (simulado).");
                throw new IllegalStateException("Sistema de autorizaciones fuera de servicio (ventana de mantenimiento)");
            }
            case LENTO -> {
                estado.registrar("Obra social (SOAP)", operacion, "respuesta demorada 8 s");
                dormir(8_000);
            }
            default -> {
            }
        }
    }

    private static String nuevaTransaccion() {
        return String.valueOf(TRANSACCIONES.incrementAndGet());
    }

    private static void dormir(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
