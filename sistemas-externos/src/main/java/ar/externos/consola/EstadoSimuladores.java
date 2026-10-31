package ar.externos.consola;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Estado de los tres sistemas externos simulados. La consola de la demo lo cambia en caliente para
 * mostrar cómo reacciona MediConecta cuando un tercero se cae o responde lento.
 */
@ApplicationScoped
public class EstadoSimuladores {

    /** Comportamiento del sistema legado de la obra social. */
    public enum ModoObraSocial { EN_LINEA, LENTO, CAIDO }

    /** Qué responde la auditoría médica de la obra social a las solicitudes de autorización. */
    public enum ModoAutorizacion { APROBAR, RECHAZAR }

    public enum ModoServicio { EN_LINEA, CAIDO }

    private static final int MAXIMO_REGISTROS = 60;

    private volatile ModoObraSocial obraSocial = ModoObraSocial.EN_LINEA;
    private volatile ModoAutorizacion autorizaciones = ModoAutorizacion.APROBAR;
    private volatile ModoServicio pasarela = ModoServicio.EN_LINEA;
    private volatile ModoServicio videoconsulta = ModoServicio.EN_LINEA;
    private volatile long demoraAutorizacionMs = 1500;

    private final Deque<RegistroLlamada> registro = new ArrayDeque<>();

    public synchronized void registrar(String sistema, String operacion, String resultado) {
        registro.addFirst(new RegistroLlamada(LocalDateTime.now(), sistema, operacion, resultado));
        while (registro.size() > MAXIMO_REGISTROS) {
            registro.removeLast();
        }
    }

    public synchronized List<RegistroLlamada> getRegistro() {
        return new ArrayList<>(registro);
    }

    public ModoObraSocial getObraSocial() {
        return obraSocial;
    }

    public void setObraSocial(ModoObraSocial obraSocial) {
        this.obraSocial = obraSocial;
    }

    public ModoAutorizacion getAutorizaciones() {
        return autorizaciones;
    }

    public void setAutorizaciones(ModoAutorizacion autorizaciones) {
        this.autorizaciones = autorizaciones;
    }

    public ModoServicio getPasarela() {
        return pasarela;
    }

    public void setPasarela(ModoServicio pasarela) {
        this.pasarela = pasarela;
    }

    public ModoServicio getVideoconsulta() {
        return videoconsulta;
    }

    public void setVideoconsulta(ModoServicio videoconsulta) {
        this.videoconsulta = videoconsulta;
    }

    public long getDemoraAutorizacionMs() {
        return demoraAutorizacionMs;
    }

    public void setDemoraAutorizacionMs(long demoraAutorizacionMs) {
        this.demoraAutorizacionMs = Math.max(0, Math.min(demoraAutorizacionMs, 20_000));
    }

    public record RegistroLlamada(LocalDateTime cuando, String sistema, String operacion, String resultado) {
    }
}
