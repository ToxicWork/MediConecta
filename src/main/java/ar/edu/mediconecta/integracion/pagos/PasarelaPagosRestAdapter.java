package ar.edu.mediconecta.integracion.pagos;

import ar.edu.mediconecta.integracion.ConfiguracionIntegraciones;
import ar.edu.mediconecta.integracion.RegistroIntegraciones;
import ar.edu.mediconecta.integracion.SistemaExterno;
import ar.edu.mediconecta.integracion.resiliencia.CircuitBreaker;
import ar.edu.mediconecta.integracion.resiliencia.CircuitoAbiertoException;
import ar.edu.mediconecta.integracion.resiliencia.EstadoCircuito;
import ar.edu.mediconecta.integracion.resiliencia.IntegracionNoDisponibleException;
import ar.edu.mediconecta.integracion.resiliencia.Reintento;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Adapter hacia la API REST de la pasarela (JAX-RS Client + JSON-B).
 *
 * <p>Retry seguro: cada cobro lleva un {@code Idempotency-Key} propio; si la respuesta se pierde y se
 * reintenta, la pasarela devuelve el mismo pago en lugar de cobrar dos veces. Circuit Breaker igual que
 * con el legado, pero sin fallback: no se confirma un turno particular sin cobrarlo.
 */
@ApplicationScoped
public class PasarelaPagosRestAdapter implements PagosGateway {

    @Inject
    private ConfiguracionIntegraciones config;

    @Inject
    private RegistroIntegraciones registro;

    private final CircuitBreaker circuito = new CircuitBreaker("Pasarela de pagos (REST)", 3, Duration.ofSeconds(30));
    private Client cliente;

    @PostConstruct
    void iniciar() {
        cliente = ClientBuilder.newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build();
    }

    @PreDestroy
    void cerrar() {
        cliente.close();
    }

    @Override
    public ResultadoCobro cobrar(PedidoCobro pedido) {
        String claveIdempotencia = UUID.randomUUID().toString();
        PedidoPagoJson cuerpo = new PedidoPagoJson(pedido.referencia(), pedido.descripcion(), pedido.importe(),
                pedido.moneda(), new TarjetaJson(pedido.numeroTarjeta(), pedido.titular(), pedido.vencimiento(),
                pedido.codigoSeguridad()));
        return llamar("POST /pagos", pedido.referencia(), RegistroIntegraciones.OK, () -> {
            try (Response respuesta = cliente.target(config.urlPasarela()).path("pagos")
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + config.clavePasarela())
                    .header("Idempotency-Key", claveIdempotencia)
                    .post(Entity.json(cuerpo))) {
                return interpretar(respuesta);
            }
        });
    }

    @Override
    public ResultadoCobro reembolsar(String idPago, String motivo, String referencia) {
        return llamar("POST /pagos/" + idPago + "/reembolso", referencia, RegistroIntegraciones.OK,
                () -> postReembolso(idPago, motivo));
    }

    @Override
    public ResultadoCobro reembolsarCompensacion(String idPago, String motivo, String referencia) {
        return llamar("POST /pagos/" + idPago + "/reembolso (compensación)", referencia,
                RegistroIntegraciones.COMPENSACION, () -> postReembolso(idPago, motivo));
    }

    private ResultadoCobro postReembolso(String idPago, String motivo) {
        try (Response respuesta = cliente.target(config.urlPasarela()).path("pagos").path(idPago).path("reembolso")
                .request(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + config.clavePasarela())
                .post(Entity.json(Map.of("motivo", motivo)))) {
            return interpretar(respuesta);
        }
    }

    @Override
    public EstadoCircuito estadoCircuito() {
        return circuito.instantanea();
    }

    private ResultadoCobro llamar(String operacion, String referencia, String resultadoExitoso,
                                  Supplier<ResultadoCobro> llamada) {
        long inicio = System.nanoTime();
        try {
            ResultadoCobro resultado = Reintento.ejecutar(2, Duration.ofMillis(300),
                    () -> circuito.ejecutar(() -> conManejoDeErrores(llamada)));
            registro.registrar(SistemaExterno.PASARELA_PAGOS, operacion, referencia,
                    resultado.aprobado() || "REEMBOLSADO".equals(resultado.estado())
                            ? resultadoExitoso : RegistroIntegraciones.RECHAZO,
                    resultado.resumen(), milis(inicio));
            return resultado;
        } catch (IntegracionNoDisponibleException falla) {
            registro.registrar(SistemaExterno.PASARELA_PAGOS, operacion, referencia,
                    falla instanceof CircuitoAbiertoException ? RegistroIntegraciones.CIRCUITO_ABIERTO : RegistroIntegraciones.ERROR,
                    falla.getMessage(), milis(inicio));
            throw falla;
        }
    }

    private static ResultadoCobro conManejoDeErrores(Supplier<ResultadoCobro> llamada) {
        try {
            return llamada.get();
        } catch (ProcessingException sinRespuesta) {
            throw new IntegracionNoDisponibleException("La pasarela no respondió: "
                    + sinRespuesta.getClass().getSimpleName(), true, sinRespuesta);
        }
    }

    private static ResultadoCobro interpretar(Response respuesta) {
        int estado = respuesta.getStatus();
        if (estado == 200 || estado == 201) {
            PagoJson pago = respuesta.readEntity(PagoJson.class);
            return new ResultadoCobro("APROBADO".equals(pago.estado()), pago.id(), pago.estado(), pago.motivoRechazo(),
                    pago.importe(), pago.moneda(), pago.marca(), pago.ultimos4());
        }
        if (estado == 422 || estado == 409) {
            ErrorJson error = respuesta.readEntity(ErrorJson.class);
            return new ResultadoCobro(false, null, "RECHAZADO", error.error(), null, null, null, null);
        }
        throw new IntegracionNoDisponibleException("La pasarela respondió HTTP " + estado, estado >= 500);
    }

    private static long milis(long inicioNanos) {
        return (System.nanoTime() - inicioNanos) / 1_000_000;
    }

    public record TarjetaJson(String numero, String titular, String vencimiento, String codigoSeguridad) {
    }

    public record PedidoPagoJson(String referencia, String descripcion, BigDecimal importe, String moneda,
                                 TarjetaJson tarjeta) {
    }

    public record PagoJson(String id, String estado, String motivoRechazo, BigDecimal importe, String moneda,
                           String marca, String ultimos4, String referencia) {
    }

    public record ErrorJson(String error, String mensaje) {
    }
}
