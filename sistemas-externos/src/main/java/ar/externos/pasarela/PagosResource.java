package ar.externos.pasarela;

import ar.externos.consola.EstadoSimuladores;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * API REST de la pasarela de pagos (partner moderno, simulado). Tarjetas de prueba:
 * <ul>
 *   <li>terminada en 0002: rechazada por fondos insuficientes;</li>
 *   <li>terminada en 0069: rechazada por tarjeta vencida;</li>
 *   <li>cualquier otra con vencimiento futuro: aprobada.</li>
 * </ul>
 * Soporta {@code Idempotency-Key}: repetir un POST con la misma clave devuelve el mismo pago, así el
 * cliente puede reintentar sin riesgo de cobrar dos veces.
 */
@Path("/pasarela/v1/pagos")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PagosResource {

    private static final DateTimeFormatter VENCIMIENTO = DateTimeFormatter.ofPattern("MM/yy");

    @Inject
    private RegistroPagos registro;

    @Inject
    private EstadoSimuladores estado;

    @POST
    public Response crear(@HeaderParam("Idempotency-Key") String claveIdempotencia, PedidoPago pedido) {
        if (estado.getPasarela() == EstadoSimuladores.ModoServicio.CAIDO) {
            estado.registrar("Pasarela (REST)", "POST /pagos", "503 servicio no disponible");
            return noDisponible();
        }
        Optional<Pago> previo = registro.porClave(claveIdempotencia);
        if (previo.isPresent()) {
            estado.registrar("Pasarela (REST)", "POST /pagos (reintento)", "200 mismo pago " + previo.get().getId());
            return Response.ok(previo.get()).build();
        }
        if (pedido == null || pedido.tarjeta() == null || pedido.importe() == null
                || pedido.importe().compareTo(BigDecimal.ZERO) <= 0) {
            return Response.status(422).entity(Map.of("error", "pedido_invalido",
                    "mensaje", "Faltan el importe o los datos de la tarjeta.")).build();
        }
        String numero = pedido.tarjeta().numero() == null ? "" : pedido.tarjeta().numero().replaceAll("\\D", "");
        if (numero.length() < 13 || numero.length() > 19) {
            return Response.status(422).entity(Map.of("error", "tarjeta_invalida",
                    "mensaje", "El número de tarjeta no es válido.")).build();
        }
        String ultimos4 = numero.substring(numero.length() - 4);
        String motivoRechazo = motivoRechazo(numero, pedido.tarjeta().vencimiento());
        Pago pago = new Pago("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16),
                motivoRechazo == null ? Pago.Estado.APROBADO : Pago.Estado.RECHAZADO, motivoRechazo,
                pedido.importe(), pedido.moneda() == null ? "ARS" : pedido.moneda(), pedido.referencia(),
                pedido.descripcion(), marca(numero), ultimos4);
        registro.guardar(pago, claveIdempotencia);
        estado.registrar("Pasarela (REST)", "POST /pagos " + pedido.referencia(),
                "201 " + pago.getEstado() + " " + pago.getId() + (motivoRechazo == null ? "" : " (" + motivoRechazo + ")"));
        return Response.status(Response.Status.CREATED).entity(pago).build();
    }

    @POST
    @Path("/{id}/reembolso")
    public Response reembolsar(@PathParam("id") String id, PedidoReembolso pedido) {
        if (estado.getPasarela() == EstadoSimuladores.ModoServicio.CAIDO) {
            estado.registrar("Pasarela (REST)", "POST /pagos/" + id + "/reembolso", "503 servicio no disponible");
            return noDisponible();
        }
        Optional<Pago> pago = registro.buscar(id);
        if (pago.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "pago_inexistente")).build();
        }
        Pago p = pago.get();
        if (p.getEstado() == Pago.Estado.RECHAZADO) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("error", "pago_rechazado",
                    "mensaje", "Un pago rechazado no se puede reembolsar.")).build();
        }
        if (p.getEstado() == Pago.Estado.APROBADO) { // idempotente: reembolsar dos veces no hace nada nuevo
            p.reembolsar(pedido == null ? null : pedido.motivo());
        }
        estado.registrar("Pasarela (REST)", "POST /pagos/" + id + "/reembolso", "200 REEMBOLSADO");
        return Response.ok(p).build();
    }

    @GET
    @Path("/{id}")
    public Response consultar(@PathParam("id") String id) {
        return registro.buscar(id).map(p -> Response.ok(p).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).entity(Map.of("error", "pago_inexistente")).build());
    }

    private static String motivoRechazo(String numero, String vencimiento) {
        if (numero.endsWith("0002")) {
            return "fondos_insuficientes";
        }
        if (numero.endsWith("0069")) {
            return "tarjeta_vencida";
        }
        try {
            if (vencimiento == null || YearMonth.parse(vencimiento, VENCIMIENTO).isBefore(YearMonth.now())) {
                return "tarjeta_vencida";
            }
        } catch (RuntimeException e) {
            return "vencimiento_invalido";
        }
        return null;
    }

    private static String marca(String numero) {
        return switch (numero.charAt(0)) {
            case '4' -> "VISA";
            case '5' -> "MASTERCARD";
            case '3' -> "AMEX";
            default -> "OTRA";
        };
    }

    private static Response noDisponible() {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", "servicio_no_disponible", "mensaje", "La pasarela está en mantenimiento."))
                .build();
    }

    public record Tarjeta(String numero, String titular, String vencimiento, String codigoSeguridad) {
    }

    public record PedidoPago(String referencia, String descripcion, BigDecimal importe, String moneda, Tarjeta tarjeta) {
    }

    public record PedidoReembolso(String motivo) {
    }
}
