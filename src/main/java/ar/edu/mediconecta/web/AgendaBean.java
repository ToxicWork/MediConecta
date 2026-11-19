package ar.edu.mediconecta.web;

import ar.edu.mediconecta.modelo.ErrorDeNegocioException;
import ar.edu.mediconecta.modelo.EstadoCobertura;
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
import jakarta.faces.component.UIInput;
import jakarta.faces.component.UIViewRoot;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.primefaces.PrimeFaces;

import java.io.Serializable;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Named
@SessionScoped
public class AgendaBean implements Serializable {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("EEEE dd/MM 'a las' HH:mm", ES_AR);
    private static final String MIS_TURNOS = AgendaBean.class.getName() + ".misTurnos";
    private static final String SESION_VENCIDA = "Tu sesión venció: volvé a ingresar.";
    private static final String TURNOS_PROFESIONAL = AgendaBean.class.getName() + ".turnosProfesional";

    @Inject
    private ServicioDeUsuarios servicioDeUsuarios;

    /** La conversación de reserva (@Stateful), que se renueva sola si venció (D-43). */
    @Inject
    private ConversacionDeTurnos turnos;

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
            disponibilidad = turnos.llamar(s -> s.buscarDisponibilidad(profesionalSeleccionadoId));
        }
    }

    /** Precio de lista de la prestación del turno (catálogo Singleton). */
    public BigDecimal precio(Turno turno) {
        return catalogo.para(turno).getPrecio();
    }

    /**
     * Próximos turnos del paciente: los que ya empezaron no se pueden cancelar y salen de esta pantalla (D-13).
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
                clave -> turnos.llamar(s -> s.buscarProximosConfirmados(usuario.getId())));
    }

    @SuppressWarnings("unchecked")
    public List<Turno> getTurnosConfirmadosProfesional() {
        Usuario usuario = loginBean.getUsuarioActual();
        if (usuario == null || !loginBean.isProfesional()) {
            return List.of();
        }
        Map<String, Object> request = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
        return (List<Turno>) request.computeIfAbsent(TURNOS_PROFESIONAL,
                clave -> turnos.llamar(s -> s.buscarProximosConfirmados(usuario.getId())));
    }

    private void invalidarMisTurnos() {
        Map<String, Object> request = FacesContext.getCurrentInstance().getExternalContext().getRequestMap();
        request.remove(MIS_TURNOS);
        request.remove(TURNOS_PROFESIONAL);
    }

    /** Fecha y hora como se muestran en las tablas y en los mensajes ("dd/MM/yyyy HH:mm"). */
    public String formatear(LocalDateTime fechaHora) {
        return fechaHora == null ? "" : FORMATO_FECHA.format(fechaHora);
    }

    /** El paciente cancela un turno suyo; el profesional, uno de su agenda (el horario no se vuelve a ofrecer). */
    public void cancelar(Long turnoId) {
        try {
            Turno cancelado = turnos.llamar(s -> s.cancelar(turnoId));
            invalidarMisTurnos();
            boolean habiaPago = cancelado.getImportePaciente() != null && cancelado.getImportePaciente().signum() > 0;
            String reintegro = habiaPago ? pesos(cancelado.getImportePaciente()) : "";
            if (loginBean.isProfesional()) {
                info("Cancelaste el turno de " + cancelado.getPaciente().getNombreCompleto() + " del "
                        + FORMATO_FECHA.format(cancelado.getFechaHora()) + ". Le avisamos al paciente"
                        + (habiaPago ? " y le reintegramos " + reintegro : "") + ". El horario no se vuelve a ofrecer.");
            } else {
                info("Turno cancelado" + (habiaPago ? ". El reintegro de " + reintegro
                        + " se procesa en unos segundos (Pagos)." : "."));
            }
        } catch (ErrorDeNegocioException e) {
            error(e.getMessage());
        }
    }

    public void reservarHold(Long turnoId) {
        try {
            Usuario usuario = loginBean.getUsuarioActual();
            if (usuario == null) {
                error(SESION_VENCIDA);
                return;
            }
            turnoEnHold = turnos.llamar(s -> s.reservarHold(turnoId, usuario.getId()));
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
            cotizacion = turnos.llamar(ServicioDeTurnos::cotizarTurnoEnHold);
        } catch (ErrorDeNegocioException e) {
            sincronizarHold();
            error(e.getMessage());
        }
    }

    /**
     * Paso 1 del pago (D-19, D-29): revisa la tarjeta sin llamar a la pasarela. Si falta algo, un único mensaje dice
     * todo lo que hay que corregir y marca los campos; si está completa, la vista abre el diálogo que pide confirmar
     * el importe ("Sí, pagar" llama a {@link #confirmar()}).
     */
    public void revisarPago() {
        Optional<String> problema = datosPago.mensajeDeError();
        if (problema.isPresent()) {
            marcarConError(datosPago.camposConError());
            error(problema.get());
            return;
        }
        PrimeFaces.current().ajax().addCallbackParam("pagoListo", true);
    }

    /** Lo que muestra el diálogo antes de cobrar (D-29): importe, tarjeta, día y profesional. */
    public String getResumenDelPago() {
        if (turnoEnHold == null || cotizacion == null) {
            return "";
        }
        return "Vas a pagar " + pesos(cotizacion.getImporte().getImportePaciente()) + " con la tarjeta terminada en "
                + datosPago.getUltimosDigitos() + " para confirmar tu turno del "
                + FORMATO_DIA.format(turnoEnHold.getFechaHora()) + " hs con "
                + turnoEnHold.getProfesional().getNombreCompleto() + ".";
    }

    /** Importe como lo leen los pacientes: "$ 9.600,00". */
    static String pesos(BigDecimal importe) {
        return NumberFormat.getCurrencyInstance(ES_AR).format(importe);
    }

    /** Hora hasta la que se guarda el turno en espera ("14:35"). */
    public String getHoraLimite() {
        return turnoEnHold == null || turnoEnHold.getHoldVenceEn() == null ? ""
                : FORMATO_HORA.format(turnoEnHold.getHoldVenceEn());
    }

    /**
     * Vuelve a preguntarle la cobertura a la obra social cuando la primera consulta quedó pendiente (D-20). Siempre
     * informa el resultado, para que el botón no parezca no hacer nada.
     */
    public void reconsultarCobertura() {
        cotizar();
        if (cotizacion == null) {
            return;
        }
        if (cotizacion.getCobertura().getEstado() == EstadoCobertura.PENDIENTE) {
            info("Tu obra social todavía no responde. Podés confirmar igual: la cobertura se completa cuando conteste.");
        } else {
            info("Listo: tu obra social respondió y actualizamos la cobertura y el importe.");
        }
    }

    public void confirmar() {
        try {
            boolean conPago = cotizacion != null && cotizacion.isRequierePago();
            if (conPago && datosPago.mensajeDeError().isPresent()) {
                marcarConError(datosPago.camposConError());
                error(datosPago.mensajeDeError().get());
                return;
            }
            String ultimos = conPago ? datosPago.getUltimosDigitos() : "";
            DatosPago pago = conPago ? datosPago : null;
            Turno confirmado = turnos.llamar(s -> s.confirmar(pago));
            invalidarMisTurnos();
            turnoEnHold = null;
            cotizacion = null;
            datosPago = new DatosPago();
            StringBuilder mensaje = new StringBuilder("Turno confirmado para el ")
                    .append(FORMATO_FECHA.format(confirmado.getFechaHora())).append(" hs.");
            if (confirmado.getImportePaciente() != null && confirmado.getImportePaciente().signum() > 0) {
                mensaje.append(" Se cobraron ").append(pesos(confirmado.getImportePaciente()));
                if (!ultimos.isEmpty()) {
                    mensaje.append(" con la tarjeta terminada en ").append(ultimos);
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
            turnos.ejecutar(ServicioDeTurnos::liberarHold);
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
        turnoEnHold = turnos.llamar(ServicioDeTurnos::getTurnoEnHold);
        if (turnoEnHold == null) {
            cotizacion = null;
        }
    }

    public void publicarDisponibilidad() {
        try {
            Usuario actual = loginBean.getUsuarioActual();
            if (actual == null) {
                error(SESION_VENCIDA);
                return;
            }
            LocalDateTime fechaHora = nuevaFechaHora;
            turnos.llamar(s -> s.publicarDisponibilidad(actual.getId(), fechaHora, nuevaModalidad));
            nuevaFechaHora = null;
            info("Disponibilidad publicada");
        } catch (EJBAccessException e) {
            error("No tenés permiso para publicar disponibilidad.");
        } catch (ErrorDeNegocioException e) {
            error(e.getMessage());
        }
    }

    /** Marca en rojo los campos de la tarjeta con problemas (la vista vuelve a dibujar sólo esos). */
    private static void marcarConError(Set<String> campos) {
        UIViewRoot raiz = FacesContext.getCurrentInstance().getViewRoot();
        for (String campo : campos) {
            if (raiz.findComponent("formConfirmacion:" + campo) instanceof UIInput entrada) {
                entrada.setValid(false);
            }
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
