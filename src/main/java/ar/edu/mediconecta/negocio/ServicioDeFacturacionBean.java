package ar.edu.mediconecta.negocio;

import ar.edu.mediconecta.dao.ReclamoObraSocialDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.dao.UsuarioDAO;
import ar.edu.mediconecta.mensajeria.EventoTurno;
import ar.edu.mediconecta.modelo.ReclamoObraSocial;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.modelo.Usuario;
import jakarta.annotation.Resource;
import jakarta.annotation.security.DeclareRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;
import java.util.logging.Logger;

@Stateless
@DeclareRoles({"PROFESIONAL", "SISTEMA"})
public class ServicioDeFacturacionBean implements ServicioDeFacturacion {

    private static final Logger LOG = Logger.getLogger(ServicioDeFacturacionBean.class.getName());

    @Inject
    private ReclamoObraSocialDAO reclamoDAO;

    @Inject
    private TurnoDAO turnoDAO;

    @Inject
    private UsuarioDAO usuarioDAO;

    @Inject
    private ServicioDePagos pagos;

    @Resource
    private SessionContext contexto;

    /**
     * Sólo le llegan TURNO_AUTORIZADO y TURNO_CANCELADO: el resto lo filtra el broker con el selector de la
     * suscripción (Selective Consumer). Corre con la identidad SISTEMA del MDB.
     */
    @Override
    @RolesAllowed("SISTEMA")
    public void procesarEvento(EventoTurno evento) {
        Turno turno = turnoDAO.buscarPorId(evento.turnoId());
        if (turno == null) {
            return;
        }
        switch (evento.tipo()) {
            case TURNO_AUTORIZADO -> {
                if (turno.getImporteObraSocial() != null && turno.getImporteObraSocial().signum() > 0
                        && reclamoDAO.buscarVigenteDeTurno(turno.getId()).isEmpty()) {
                    reclamoDAO.guardar(new ReclamoObraSocial(turno, turno.getImporteObraSocial()));
                    LOG.info("[Facturación] Reclamo a " + turno.getObraSocial() + " por $" + turno.getImporteObraSocial()
                            + " (turno " + turno.getId() + ", autorización " + turno.getCodigoAutorizacion() + ").");
                }
            }
            case TURNO_CANCELADO -> {
                reclamoDAO.buscarVigenteDeTurno(turno.getId()).ifPresent(ReclamoObraSocial::anular);
                pagos.reembolsarPagoDeTurno(turno.getId(), "Cancelación del turno por el paciente");
                LOG.info("[Facturación] Turno " + turno.getId() + " cancelado: reclamo anulado y pago reintegrado (si lo había).");
            }
            default -> LOG.warning("[Facturación] Evento inesperado " + evento.tipo() + ": revisar el selector.");
        }
    }

    @Override
    @RolesAllowed("PROFESIONAL")
    public List<ReclamoObraSocial> reclamosDelProfesionalActual() {
        Usuario usuario = usuarioDAO.buscarPorEmail(contexto.getCallerPrincipal().getName()).orElse(null);
        return usuario == null ? List.of() : reclamoDAO.buscarDeProfesional(usuario.getId());
    }
}
