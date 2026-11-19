# ADR-0023: Ficha del paciente con el Decorator PacienteEnmascarado

- **Estado:** aceptado (19/11/2026)
- **Fecha:** 19/11/2026
- **Patrón:** Decorator (GoF), como decorador de CDI
- **Cumple:** el documento de diseño de patrones y seguridad, sección 4.8. Hasta esta versión no estaba hecho, y el
  informe de la entrega del 16/11 lo daba como "previsto para la entrega final".

## Contexto

El diseño dice: "La ficha del paciente la consultan profesionales y administrativos, pero solo el profesional debe ver
los datos clínicos. Meter esa regla dentro de PacienteService mezcla negocio con control de acceso." Propone un
decorador de CDI que envuelva al servicio y enmascare los datos clínicos cuando quien llama no tiene el rol PROFESIONAL.

La aplicación no tenía una ficha del paciente. El administrativo veía en Usuarios sólo el nombre y la obra social, y el
profesional, la historia clínica completa de sus pacientes (SEC-D01: con un turno confirmado con él).

## Decisión

- **Componente:** `negocio/ServicioDePacientes`, con `ficha(pacienteId)`. Es el `PacienteService` del diseño, con el
  nombre en español como el resto de los servicios.
- **Componente concreto:** `negocio/ServicioDePacientesBean`, un EJB `@Stateless` con
  `@RolesAllowed({"PROFESIONAL", "ADMINISTRATIVO"})` y `@Auditado(recurso = "ficha_paciente")`. Arma la ficha
  completa (`negocio/dto/FichaPaciente`):
  - la identificación;
  - la obra social y el n.º de afiliado;
  - las 3 entradas más recientes de la historia clínica y cuántas hay.
- **Decorador:** `negocio/PacienteEnmascarado`, con `@Decorator` y `@Priority(APPLICATION)`, que lo habilita sin
  tocar `beans.xml`. Devuelve `ficha.sinDatosClinicos()` (sin entradas y sin el total) salvo que quien llama sea un
  **profesional con un turno confirmado con ese paciente**.
- **Diferencia con el diseño:** el diseño pedía sólo el rol PROFESIONAL. Le sumamos la regla de titularidad de la
  historia clínica (SEC-D01) porque, si no, cualquier profesional vería por la ficha lo que no puede ver en la
  historia. El administrativo sigue viendo el n.º de afiliado, que necesita para las autorizaciones y los reclamos a
  la obra social.
- **Pantallas:** un diálogo "Ficha del paciente" (`WEB-INF/includes/ficha-paciente.xhtml`, `web/FichaPacienteBean`)
  que se abre desde:
  - Usuarios, para el administrativo;
  - los próximos turnos de la Agenda y el encabezado de Historia clínica, para el profesional.

  La pantalla no decide nada: muestra lo que viene y, si la ficha llega enmascarada, explica que los datos clínicos
  sólo los ve el profesional que atiende al paciente.

## Alternativas

- **Dos métodos en el servicio** (`fichaCompleta` y `fichaSinDatosClinicos`): el diseño lo descartó, porque cada
  pantalla tendría que elegir bien y la regla quedaría repartida.
- **Un interceptor:** también separa la regla del negocio, pero un decorador conoce la interfaz y trabaja con la
  ficha ya tipada, sin reflexión.
- **Filtrar en la vista:** es lo que el diseño quería evitar. Además, el dato llegaría igual al navegador.

## Consecuencias

- **A favor:**
  - La regla de privacidad queda en un solo lugar y el servicio no cambia.
  - Cualquier pantalla nueva que pida la ficha recibe la versión que corresponde.
  - Cada consulta queda en la auditoría.
- **En contra:** el decorador consulta la base (el usuario y si hay un turno confirmado) en cada ficha que pide un
  profesional. Es una consulta liviana por ficha.
- **Pruebas:**
  - `PacienteEnmascaradoTest`: el profesional tratante ve todo; el administrativo, la identificación y la cobertura
    pero nada clínico; un profesional sin turno confirmado y una llamada sin usuario reciben la ficha enmascarada. La
    prueba también verifica las anotaciones.
  - `pruebas/prueba_datos_protegidos.py`, escenario B: lo mismo sobre la aplicación desplegada, desde las tres
    pantallas.
