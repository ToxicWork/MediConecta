package ar.edu.mediconecta.mensajeria;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SEC-I01: el evento que viaja por {@code topico.turnos} no lleva datos clínicos. Lo reciben tres suscriptores
 * (Notificaciones, Auditoría y Facturación) y queda retenido en el broker mientras alguno esté caído, así que
 * sólo puede llevar lo que esos consumidores necesitan: datos del turno, de contacto y de cobro.
 *
 * <p>JSON-B arma el mensaje con los componentes del record, así que verificar el record es verificar el mensaje.
 *
 * <p>Decisión aceptada: la especialidad del profesional viaja porque el aviso al paciente la necesita ("tu turno
 * de Cardiología..."). Dice algo de la salud del paciente, pero no es un diagnóstico ni un dato de la historia
 * clínica; el diagnóstico, las recetas y las entradas de la historia nunca salen de ServicioDeHistoriaClinica.
 */
class EventoSinDatosClinicosTest {

    /** Lista blanca: agregar un campo al evento obliga a revisar esta prueba (y a justificarlo). */
    private static final Set<String> CAMPOS_PERMITIDOS = Set.of(
            "idEvento", "tipo", "ocurridoEn",                                  // del evento
            "turnoId", "fechaHoraTurno", "modalidad",                          // del turno
            "profesionalId", "profesionalNombre", "especialidad",              // del profesional
            "pacienteId", "pacienteNombre", "pacienteEmail",                   // contacto (Notificaciones)
            "obraSocial", "cobertura", "importePaciente", "importeObraSocial", // cobro (Facturación)
            "codigoAutorizacion", "motivoAutorizacion");                       // respuesta administrativa de la obra social

    private static final Pattern NOMBRE_CLINICO = Pattern.compile(
            "(?i).*(diagn|receta|tratamiento|historia|antecedente|sintoma|clinic|descripcion|entrada|medicac|estudio).*");

    /** Tipos simples: un objeto anidado (Paciente, HistoriaClinica...) podría arrastrar datos clínicos al JSON. */
    private static final List<Class<?>> TIPOS_SIMPLES = List.of(
            String.class, Long.class, BigDecimal.class, LocalDateTime.class, TipoEventoTurno.class);

    private static final RecordComponent[] CAMPOS = EventoTurno.class.getRecordComponents();

    @Test
    void elEventoTieneExactamenteLosCamposPermitidos() {
        Set<String> campos = Arrays.stream(CAMPOS).map(RecordComponent::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(CAMPOS_PERMITIDOS), campos,
                "cambió la forma del evento: verificar que no viajen datos clínicos y actualizar la lista blanca");
    }

    @Test
    void ningunCampoTieneNombreDeDatoClinico() {
        for (RecordComponent campo : CAMPOS) {
            assertTrue(!NOMBRE_CLINICO.matcher(campo.getName()).matches(), "campo clínico en el evento: " + campo.getName());
        }
    }

    @Test
    void ningunCampoEsUnObjetoQuePuedaArrastrarLaHistoriaClinica() {
        for (RecordComponent campo : CAMPOS) {
            assertTrue(TIPOS_SIMPLES.contains(campo.getType()),
                    "el campo " + campo.getName() + " es un " + campo.getType().getSimpleName() + ", no un tipo simple");
        }
    }
}
