package ar.edu.mediconecta.negocio.dto;

import java.io.Serializable;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Objects;

/** Totales del monitor: eventos publicados, entregas procesadas y pendientes, y latencias. */
public class ResumenMonitorDTO implements Serializable {

    private final int publicados;
    private final long procesados;
    private final long pendientes;
    private final Long latenciaPromedioMs;
    private final Long latenciaMaximaMs;

    private ResumenMonitorDTO(int publicados, long procesados, long pendientes,
                              Long latenciaPromedioMs, Long latenciaMaximaMs) {
        this.publicados = publicados;
        this.procesados = procesados;
        this.pendientes = pendientes;
        this.latenciaPromedioMs = latenciaPromedioMs;
        this.latenciaMaximaMs = latenciaMaximaMs;
    }

    public static ResumenMonitorDTO de(List<FilaMonitorDTO> filas) {
        List<EstadoSuscriptorDTO> estados = filas.stream().flatMap(f -> f.getSuscriptores().stream()).toList();
        long procesados = estados.stream().filter(EstadoSuscriptorDTO::isProcesado).count();
        LongSummaryStatistics latencias = estados.stream()
                .map(EstadoSuscriptorDTO::getLatenciaMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .summaryStatistics();
        boolean hayLatencias = latencias.getCount() > 0;
        return new ResumenMonitorDTO(filas.size(), procesados, estados.size() - procesados,
                hayLatencias ? Math.round(latencias.getAverage()) : null,
                hayLatencias ? latencias.getMax() : null);
    }

    public int getPublicados() {
        return publicados;
    }

    public long getProcesados() {
        return procesados;
    }

    public long getPendientes() {
        return pendientes;
    }

    public Long getLatenciaPromedioMs() {
        return latenciaPromedioMs;
    }

    public Long getLatenciaMaximaMs() {
        return latenciaMaximaMs;
    }
}
