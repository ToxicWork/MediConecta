# MediConecta

Plataforma que conecta pacientes con profesionales de salud independientes y pequeñas clínicas
para turnos presenciales y telemedicina, con facturación a obras sociales y prepagas.

TP Integrador, Desarrollo de Aplicaciones II (comisión Lunes TM).

## Estado actual

**Entrega Obligatoria N.º 1** (Unidades I-II-III): tres componentes completos, implementados en
capas y desplegados sobre Jakarta EE 10 (JSF + PrimeFaces, EJB, JPA/Hibernate), WildFly y
PostgreSQL (Supabase).

- `ServicioDeUsuarios`, EJB `@Stateless`. Registro y autenticación de pacientes y profesionales.
- `ServicioDeTurnos`, EJB `@Stateful` con `@StatefulTimeout`. Publicación de disponibilidad,
  reserva con hold temporal (5 min) y confirmación de turnos.
- `ServicioDeHistoriaClinica`, EJB `@Stateless` con `@RolesAllowed`. Carga y consulta de
  diagnósticos y recetas.

Patrones aplicados: DAO, Facade y Factory. Seguridad declarativa con
`@DatabaseIdentityStoreDefinition` (Jakarta Security) y `@RolesAllowed`.

Las integraciones externas (obra social vía SOAP, pagos y telemedicina vía REST) y la mensajería
asincrónica quedan para las próximas entregas (Unidad IV en adelante), según el cronograma del TP.

## Documentación

- [`SETUP.md`](SETUP.md): cómo levantar WildFly, la base en Supabase y desplegar el proyecto
  localmente.
- [`Trabajo practico/MediConecta_Documento_Tecnico_Entrega1.pdf`](<Trabajo practico/MediConecta_Documento_Tecnico_Entrega1.pdf>):
  documento técnico de la Entrega Obligatoria N.º 1 (arquitectura, componentes, patrones,
  seguridad y decisiones de diseño).
- [`Trabajo practico/MediConecta_Diagrama_Capas 1.png`](<Trabajo practico/MediConecta_Diagrama_Capas 1.png>):
  arquitectura en capas planificada para el sistema completo.

## Declaración de uso de inteligencia artificial generativa

Conforme a lo exigido en la Sección 9 del TP Integrador, se declara el uso de IA generativa
(Claude, Anthropic) como asistente de productividad durante esta entrega:

- Generación de boilerplate: entidades JPA, DAOs, EJB de negocio y vistas JSF/PrimeFaces.
- Configuración e instalación local de JDK, Maven y WildFly, y su conexión a la base de datos en
  Supabase.
- Diagnóstico y corrección de errores de integración entre Jakarta Security y WildFly 41 al
  combinarlos con un login a medida (detallado en el documento técnico, Sección 6).
- Redacción del documento técnico de esta entrega.

Cada integrante del equipo puede explicar y defender individualmente cualquier parte del código o
del diseño en la instancia oral, incluidas las decisiones tomadas con asistencia de IA.
