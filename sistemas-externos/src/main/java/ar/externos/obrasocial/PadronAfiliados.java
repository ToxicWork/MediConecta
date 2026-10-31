package ar.externos.obrasocial;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Reglas del padrón del legado. Para la demo, la cobertura se decide por el nombre de la obra social
 * que declaró el paciente al registrarse (no hay un padrón real de afiliados):
 *
 * <ul>
 *   <li>OSDE: cubre el 70 %, el afiliado paga el 30 % (copago porcentual).</li>
 *   <li>Swiss Medical: copago de monto fijo ($3.500).</li>
 *   <li>PAMI, IOMA: exento de copago (la obra social paga el 100 %).</li>
 *   <li>Galeno: afiliado suspendido.</li>
 *   <li>Cualquier otra: obra social no adherida a la red (atención particular).</li>
 * </ul>
 */
final class PadronAfiliados {

    private PadronAfiliados() {
    }

    static ResultadoCobertura consultar(String nroTransaccion, ConsultaCobertura consulta) {
        String obraSocial = consulta.getObraSocial() == null ? "" : consulta.getObraSocial().toUpperCase(Locale.ROOT);
        String plan = consulta.getPlan();
        if (obraSocial.contains("OSDE")) {
            return new ResultadoCobertura(nroTransaccion, EstadoCobertura.CUBIERTO, plan == null ? "210" : plan,
                    TipoCopago.PORCENTAJE, new BigDecimal("30"), true, "Cobertura vigente. Copago del 30 %.");
        }
        if (obraSocial.contains("SWISS")) {
            return new ResultadoCobertura(nroTransaccion, EstadoCobertura.CUBIERTO, plan == null ? "SMG20" : plan,
                    TipoCopago.MONTO_FIJO, new BigDecimal("3500"), true, "Cobertura vigente. Copago fijo.");
        }
        if (obraSocial.contains("PAMI") || obraSocial.contains("IOMA")) {
            return new ResultadoCobertura(nroTransaccion, EstadoCobertura.CUBIERTO, plan == null ? "GENERAL" : plan,
                    TipoCopago.EXENTO, BigDecimal.ZERO, true, "Cobertura total, exento de copago.");
        }
        if (obraSocial.contains("GALENO")) {
            return new ResultadoCobertura(nroTransaccion, EstadoCobertura.AFILIADO_SUSPENDIDO, plan,
                    TipoCopago.NINGUNO, null, false, "Afiliado suspendido por falta de pago.");
        }
        return new ResultadoCobertura(nroTransaccion, EstadoCobertura.NO_CUBIERTO, plan,
                TipoCopago.NINGUNO, null, false, "La obra social no está adherida a la red de prestadores.");
    }
}
