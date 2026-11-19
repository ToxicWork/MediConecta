package ar.edu.mediconecta.it;

import ar.edu.mediconecta.dao.EventoProcesadoDAO;
import ar.edu.mediconecta.dao.TurnoDAO;
import ar.edu.mediconecta.mensajeria.EventoDuplicadoException;
import ar.edu.mediconecta.modelo.Paciente;
import ar.edu.mediconecta.modelo.Profesional;
import ar.edu.mediconecta.modelo.Turno;
import ar.edu.mediconecta.seguridad.CifradorDeColumnas;
import jakarta.inject.Inject;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repositorios sobre el PostgreSQL real, dentro de WildFly (criterios de la Clase 15: "base de datos real, no mocks en
 * memoria"). Las consultas JPQL de los DAO, el cifrado de columnas con Hibernate y la clave del servidor, y la
 * idempotencia de los consumidores con la clave primaria real. Cada prueba deshace su transacción: la base queda igual.
 */
@ExtendWith(ArquillianExtension.class)
class RepositoriosIT {

    @Deployment
    static WebArchive despliegue() {
        return Despliegues.conModeloYRepositorios("repositorios-it", RepositoriosIT.class);
    }

    @Inject
    private BaseDePruebas base;

    @Inject
    private TurnoDAO turnos;

    @Inject
    private EventoProcesadoDAO eventosProcesados;

    @Test
    void lasConsultasDelTurnoConfirmadoFuncionanContraLaBaseReal() throws Exception {
        base.yDeshacer(() -> {
            String s = BaseDePruebas.sufijo();
            Profesional profesional = base.profesional(s);
            Profesional otra = base.profesional(s + "b");
            Paciente paciente = base.paciente(s, null);
            Turno turno = base.turnoConfirmado(profesional, paciente, LocalDateTime.now().plusDays(90).withNano(0));
            base.em().flush();

            assertTrue(turnos.existeConfirmadoEntre(profesional.getId(), paciente.getId()), "SEC-D01: la habilita");
            assertFalse(turnos.existeConfirmadoEntre(otra.getId(), paciente.getId()), "otra profesional, no");
            assertTrue(turnos.buscarProximosConfirmados(profesional.getId(), LocalDateTime.now(), 0, 50).stream()
                    .anyMatch(t -> t.getId().equals(turno.getId())));
            assertEquals(1, turnos.contarProximosConfirmados(profesional.getId(), LocalDateTime.now()));
            assertTrue(turnos.profesionalTieneHorarioEn(profesional.getId(), turno.getFechaHora()), "D-23: sin horarios repetidos");
            return null;
        });
    }

    @Test
    void elAfiliadoQuedaCifradoEnLaColumnaYLaAplicacionLoLeeEnClaro() throws Exception {
        base.yDeshacer(() -> {
            String afiliado = "AF-IT-" + BaseDePruebas.sufijo();
            Paciente paciente = base.paciente(BaseDePruebas.sufijo(), afiliado);
            base.em().flush();

            String enLaBase = base.columna("SELECT nro_afiliado FROM usuario WHERE id = ?1", paciente.getId());
            assertTrue(enLaBase.startsWith(CifradorDeColumnas.PREFIJO), "ADR-0022: cifrado con AES-GCM");
            assertFalse(enLaBase.contains(afiliado), "el n.º de afiliado no aparece en la base");

            base.em().clear();
            assertEquals(afiliado, base.em().find(Paciente.class, paciente.getId()).getNroAfiliado(),
                    "con la clave del credential store de Elytron se lee en claro");
            return null;
        });
    }

    @Test
    void unEventoYaProcesadoSeRechazaConLaClaveDeLaBase() throws Exception {
        base.yDeshacer(() -> {
            String evento = UUID.randomUUID().toString();
            eventosProcesados.registrar("notificaciones", evento);
            base.em().flush();

            assertThrows(EventoDuplicadoException.class, () -> eventosProcesados.registrar("notificaciones", evento),
                    "Idempotent Receiver: el mismo evento dos veces no se procesa dos veces");
            eventosProcesados.registrar("auditoria", evento); // otro suscriptor sí lo procesa
            base.em().flush();
            assertEquals(2, base.contar("SELECT count(*) FROM evento_procesado WHERE clave LIKE ?1", "%" + evento));
            return null;
        });
    }
}
