package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.Modalidad;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import ar.edu.mediconecta.negocio.ServicioDeTurnos;
import ar.edu.mediconecta.negocio.ServicioDeUsuarios;
import ar.edu.mediconecta.negocio.catalogo.CatalogoPrestaciones;
import ar.edu.mediconecta.negocio.dto.CotizacionTurnoDTO;
import ar.edu.mediconecta.negocio.dto.DatosPago;
import ar.edu.mediconecta.negocio.excepciones.PagoRequeridoException;
import jakarta.ejb.EJBAccessException;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Named
@SessionScoped
public class AgendaBean implements Serializable {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String MIS_TURNOS = AgendaBean.class.getName() + ".misTurnos";
    private static final String TURNOS_PROFESIONAL = AgendaBean.class.getName() + ".turnosProfesional";

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    @Inject
    private ServicioDeTurnos servicioDeTurnos;

    @Inject
    private CatalogoPrestaciones catalogo;

    @Inject
    private LoginBean loginBean;

    private Long profesionalSeleccionadoId;
    private List<Turno> disponibilidad = new ArrayList<>();
    private Turno turnoEnHold;
    private CotizacionTurnoDTO cotizacion;
    private DatosPago datosPago = new DatosPago();
    private LocalDateTime nuevaFechaHora;
    private Modalidad nuevaModalidad = Modalidad.PRESENCIAL;

    public String verificarAcceso() {
        return loginBean.exigirRol("PACIENTE", "PROFESIONAL");
    }

    public List<Profesional> getProfesionales() {
        return servicioDeUsuarios.listarProfesionales();
    }

    public Modalidad[] getModalidades() {
        return Modalidad.values();
    }

    /** Primer día elegible en el calendario de disponibilidad (el servicio igual rechaza horarios pasados). */
    public LocalDate getHoy() {
        return LocalDate.now();
    }

    public void buscarDisponibilidad() {
        if (profesionalSeleccionadoId != null) {
            disponibilidad = servicioDeTurnos.buscarDisponibilidad(profesionalSeleccionadoId);
        }
    }

    /** Precio de lista de la prestación del turno (catálogo Singleton). */
    public BigDecimal precio(Turno turno) {
        return catalogo.para(turno).getPrecio();
    }

    /**
     * Se evalúa varias veces por página: se guarda en el request y se invalida en las acciones que cambian
     * los turnos del paciente (la tabla lo lee antes de la acción).
     */
    @SuppressWarnings("unchecked")
    public List<Turno> getMisTurnos() {
        Usuario usuario = loginBean.getUsuarioActual();
        if (usuario == null || !loginBean.isPaciente()) {
            return List.of();
        }
        Map<String, Object> request = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
        return (List<Turno>) request.computeIfAbsent(MIS_TURNOS,
                clave -> servicioDeTurnos.buscarConfirmadosDePaciente(usuario.getId()));
    }

    @SuppressWarnings("unchecked")
    public List<Turno> getTurnosConfirmadosProfesional() {
        Usuario usuario = loginBean.getUsuarioActual();
        if (usuario == null || !loginBean.isProfesional()) {
            return List.of();
        }
        Map<String, Object> request = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
        return (List<Turno>) request.computeIfAbsent(TURNOS_PROFESIONAL,
                clave -> servicioDeTurnos.buscarConfirmadosDeProfesional(usuario.getId()));
    }

    private void invalidarMisTurnos() {
        FacesContext.getCurrentInstance().getExternalContext().getRequestMap().remove(MIS_TURNOS);
    }

    public void cancelar(Long turnoId) {
        try {
            Usuario usuario = loginBean.getUsuarioActual();
            Turno cancelado = servicioDeTurnos.cancelar(turnoId, usuario.getId());
            invalidarMisTurnos();
            boolean habiaPago = cancelado.getImportePaciente() != null && cancelado.getImportePaciente().signum() > 0;
            info("Turno cancelado" + (habiaPago ? ". El reintegro de $ " + cancelado.getImportePaciente().toPlainString()
                    + " se procesa en unos segundos (Pagos)." : "."));
        } catch (ErrorDeNegocioException e) {
            error(e.getMessage());
        }
    }

    public void reservarHold(Long turnoId) {
        try {
            Usuario usuario = loginBean.getUsuarioActual();
            turnoEnHold = servicioDeTurnos.reservarHold(turnoId, usuario.getId());
            datosPago = new DatosPago();
            cotizar();
            buscarDisponibilidad();
        } catch (ErrorDeNegocioException e) {
            error(e.getMessage());
        }
    }

    /** Consulta la cobertura a la obra social (SOAP) y calcula el copago del turno en espera. */
    public void cotizar() {
        try {
            cotizacion = servicioDeTurnos.cotizarTurnoEnHold();
        } catch (ErrorDeNegocioException e) {
            sincronizarHold();
            error(e.getMessage());
        }
    }

    public void confirmar() {
        try {
            boolean conPago = cotizacion != null && cotizacion.isRequierePago();
            String tarjeta = conPago && datosPago.getNumeroTarjeta() != null
                    ? datosPago.getNumeroTarjeta().replaceAll("\\D", "") : "";
            Turno confirmado = servicioDeTurnos.confirmar(conPago ? datosPago : null);
            invalidarMisTurnos();
            turnoEnHold = null;
            cotizacion = null;
            datosPago = new DatosPago();
            StringBuilder mensaje = new StringBuilder("Turno confirmado para el ")
                    .append(FORMATO_FECHA.format(confirmado.getFechaHora())).append(" hs.");
            if (confirmado.getImportePaciente() != null && confirmado.getImportePaciente().signum() > 0) {
                mensaje.append(" Se cobraron $ ").append(confirmado.getImportePaciente().toPlainString());
                if (tarjeta.length() >= 4) {
                    mensaje.append(" con la tarjeta terminada en ").append(tarjeta.substring(tarjeta.length() - 4));
                }
                mensaje.append('.');
            }
            if (confirmado.getEstadoAutorizacion() == ar.edu.mediconecta.modelo.EstadoAutorizacion.PENDIENTE) {
                mensaje.append(" La autorización de tu obra social se tramita en segundo plano.");
            }
            info(mensaje.toString());
        } catch (PagoRequeridoException e) {
            error(e.getMessage());
            cotizar();
        } catch (ErrorDeNegocioException e) {
            error(e.getMessage());
            sincronizarHold();
        }
    }

    public void liberarHold() {
        try {
            servicioDeTurnos.liberarHold();
            info("Liberaste el turno: volvió a estar disponible.");
        } catch (ErrorDeNegocioException e) {
            error(e.getMessage());
        }
        turnoEnHold = null;
        cotizacion = null;
        buscarDisponibilidad();
    }

    /** Tras un error, la vista vuelve a reflejar lo que la conversación (@Stateful) tiene realmente en espera. */
    private void sincronizarHold() {
        turnoEnHold = servicioDeTurnos.getTurnoEnHold();
        if (turnoEnHold == null) {
            cotizacion = null;
        }
    }

    public void publicarDisponibilidad() {
        try {
            Usuario actual = loginBean.getUsuarioActual();
            servicioDeTurnos.publicarDisponibilidad(actual.getId(), nuevaFechaHora, nuevaModalidad);
            nuevaFechaHora = null;
            info("Disponibilidad publicada");
        } catch (EJBAccessException e) {
            error("No tenés permiso para publicar disponibilidad.");
        } catch (ErrorDeNegocioException e) {
            error(e.getMessage());
        }
    }

    private static void info(String mensaje) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, mensaje, null));
    }

    private static void error(String mensaje) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_ERROR, mensaje, null));
    }

    public Long getProfesionalSeleccionadoId() {
        return profesionalSeleccionadoId;
    }

    public void setProfesionalSeleccionadoId(Long profesionalSeleccionadoId) {
        this.profesionalSeleccionadoId = profesionalSeleccionadoId;
    }

    public List<Turno> getDisponibilidad() {
        return disponibilidad;
    }

    public Turno getTurnoEnHold() {
        return turnoEnHold;
    }

    public CotizacionTurnoDTO getCotizacion() {
        return cotizacion;
    }

    public DatosPago getDatosPago() {
        return datosPago;
    }

    public LocalDateTime getNuevaFechaHora() {
        return nuevaFechaHora;
    }

    public void setNuevaFechaHora(LocalDateTime nuevaFechaHora) {
        this.nuevaFechaHora = nuevaFechaHora;
    }

    public Modalidad getNuevaModalidad() {
        return nuevaModalidad;
    }

    public void setNuevaModalidad(Modalidad nuevaModalidad) {
        this.nuevaModalidad = nuevaModalidad;
    }
}
