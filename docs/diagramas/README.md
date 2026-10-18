# Diagramas de MediConecta

Fuente en PlantUML (`.puml`) y exportado a PNG. Todos reflejan el código **tal como está implementado**;
lo planificado para el 09/11 aparece con línea punteada y está aclarado en cada diagrama.

| # | Diagrama | Tipo | Para qué entrega |
|---|---|---|---|
| 01 | `01-integracion-soa` | Arquitectura de integración (SOA) | **Parcial 2 (obligatorio)** |
| 02 | `02-componentes` | UML de componentes | Documento técnico |
| 03 | `03-despliegue` | UML de despliegue | Documento técnico |
| 04 | `04-secuencia-confirmar-turno` | UML de secuencia: proceso asincrónico | Parcial 2 / Obligatoria 2 |
| 05 | `05-secuencia-fallas` | UML de secuencia: rollback, duplicado, durable, DLQ | Obligatoria 2 (comportamiento ante fallas) |
| 06 | `06-estados-turno` | UML de estados | Documento técnico |
| 07 | `07-casos-de-uso` | UML de casos de uso | Documentación funcional (anexo) |
| 08 | `08-clases-modelo` | UML de clases: dominio | Documento técnico |
| 09 | `09-entidad-relacion` | Entidad-relación (tablas reales de Supabase) | Documento técnico |
| 10 | `10-clases-patrones` | **UML de clases con todos los patrones interrelacionados** | Patrones / documento técnico |

El diagrama 10 muestra todas las clases del sistema en un solo dibujo, con la notación UML completa
(leyenda incluida en el diagrama):

- **Asociación** `──>` con multiplicidad (`1`, `0..1`, `*`, `1..*`): la clase guarda una referencia en un
  atributo (`@Inject`, `@ManyToOne`); el texto de la flecha es el nombre del atributo.
- **Dependencia** `- - ->` con `«create»`, `«use»`, `«send»`, `«persist»`: la usa o la crea dentro de un
  método, sin guardarla.
- **Generalización** `──▷` (herencia) y **realización** `- - ▷` (implementa una interfaz).
- El **rol de cada clase en su patrón** va como estereotipo (`«Facade»`, `«Builder»`, `«DAO»`...) y cada
  patrón tiene un color, explicado en la leyenda.

## Regenerar los PNG

Requiere Java y Graphviz (`brew install graphviz`):

```bash
curl -LO https://repo1.maven.org/maven2/net/sourceforge/plantuml/plantuml/1.2024.8/plantuml-1.2024.8.jar
java -jar plantuml-1.2024.8.jar -tpng -charset UTF-8 docs/diagramas/[0-9]*.puml
```

Para editarlos sin instalar nada: pegar el `.puml` en <https://www.plantuml.com/plantuml> (incluyendo el
contenido de `_estilo.iuml` en lugar de la línea `!include`).
