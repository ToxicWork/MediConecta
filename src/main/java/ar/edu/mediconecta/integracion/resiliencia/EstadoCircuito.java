package ar.edu.mediconecta.integracion.resiliencia;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Transfer Object con el estado de un Circuit Breaker, para mostrarlo en la vista. */
public class EstadoCircuito implements Serializable {

    private final String nombre;
    private final CircuitBreaker.Estado estado;
    private final int fallasConsecutivas;
    private final int umbralFallas;
    private final long esperaSegundos;
    private final Instant abiertoHasta;
    private final long llamadasRechazadas;
    private final Instant ultimoCambio;

    public EstadoCircuito(String nombre, CircuitBreaker.Estado estado, int fallasConsecutivas, int umbralFallas,
                          long esperaSegundos, Instant abiertoHasta, long llamadasRechazadas, Instant ultimoCambio) {
        this.nombre = nombre;
        this.estado = estado;
        this.fallasConsecutivas = fallasConsecutivas;
        this.umbralFallas = umbralFallas;
        this.esperaSegundos = esperaSegundos;
        this.abiertoHasta = abiertoHasta;
        this.llamadasRechazadas = llamadasRechazadas;
        this.ultimoCambio = ultimoCambio;
    }

    public String getNombre() {
        return nombre;
    }

    public CircuitBreaker.Estado getEstado() {
        return estado;
    }

    public String getEstadoNombre() {
        return estado.name();
    }

    public int getFallasConsecutivas() {
        return fallasConsecutivas;
    }

    public int getUmbralFallas() {
        return umbralFallas;
    }

    public long getEsperaSegundos() {
        return esperaSegundos;
    }

    public LocalDateTime getAbiertoHasta() {
        return abiertoHasta == null ? null : LocalDateTime.ofInstant(abiertoHasta, ZoneId.systemDefault());
    }

    public long getSegundosParaReintentar() {
        return abiertoHasta == null ? 0 : Math.max(0, abiertoHasta.getEpochSecond() - Instant.now().getEpochSecond());
    }

    public long getLlamadasRechazadas() {
        return llamadasRechazadas;
    }

    public LocalDateTime getUltimoCambio() {
        return LocalDateTime.ofInstant(ultimoCambio, ZoneId.systemDefault());
    }
}
