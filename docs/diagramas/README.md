# Diagramas de MediConecta

Fuente en PlantUML (`.puml`) y exportado a PNG. Todos reflejan el código **tal como está implementado**.

| # | Diagrama | Tipo | Entrega |
|---|---|---|---|
| 01 | `01-integracion-soa` | Arquitectura de integración (SOA): componentes, sistemas externos y canales | **Actualizado: Obligatoria 2** (todo implementado) |
| 02 | `02-componentes` | UML de componentes e interfaces (incluye gateways, adaptadores y usuarios) | **Actualizado: noviembre 2026** |
| 03 | `03-despliegue` | UML de despliegue | Parcial 2 |
| 04 | `04-secuencia-confirmar-turno` | Secuencia: proceso asincrónico del tópico | Parcial 2 |
| 05 | `05-secuencia-fallas` | Secuencia: rollback, duplicado, durable, DLQ | Parcial 2 |
| 06 | `06-estados-turno` | UML de estados del turno (patrón State, CANCELADO como estado final, vencimiento de la disponibilidad) | **Actualizado: noviembre 2026** |
| 07 | `07-casos-de-uso` | UML de casos de uso (cobertura, pago, autorización, facturación, teleconsulta, API, administrativo, perfil) | **Actualizado: noviembre 2026** |
| 08 | `08-clases-modelo` | UML de clases del dominio | Parcial 2 |
| 09 | `09-entidad-relacion` | Entidad-relación (tablas reales de Supabase, con las nuevas) | **Actualizado: noviembre 2026** |
| 10 | `10-clases-patrones` | UML de clases con los patrones de las Entregas 1 y Parcial 2 (histórico: incluye el monitor de eventos, retirado en noviembre de 2026) | Parcial 2 |
| 11 | `11-secuencia-cobertura-soap` | **Secuencia SOAP**: cotización contra el legado (WS-Security, retry, Circuit Breaker, fallback) | Obligatoria 2 |
| 12 | `12-secuencia-confirmacion-transaccional` | **Secuencia del flujo crítico**: SOAP + REST + cola + tópico en una transacción JTA | Obligatoria 2 |
| 13 | `13-secuencia-falla-compensacion` | **Secuencia de una falla a mitad del flujo**: rollback y reembolso compensatorio | Obligatoria 2 |
| 14 | `14-secuencia-cola-autorizaciones` | **Secuencia de la cola punto a punto** con el legado caído (reintentos, Competing Consumers, Selective Consumer) | Obligatoria 2 |
| 15 | `15-secuencia-cancelacion-reintegro` | **Secuencia del tópico + REST**: cancelación con reintegro asincrónico | Obligatoria 2 |
| 16 | `16-secuencia-api-rest-telemedicina` | **Secuencias REST**: API propia (servidor) y videoconsulta (cliente) | Obligatoria 2 |
| 17 | `17-clases-patrones-entrega4` | UML de clases de los patrones incorporados (Adapter, Circuit Breaker, Strategy, Chain, State, Intercepting Filter, Singleton, compensación) | Obligatoria 2 |
| 18 | `18-clases-usuarios-y-roles` | UML de clases de los ajustes: Factory con el rol Administrativo, seguridad declarativa por operación, contraseña temporal, timers de vencimiento y administrador por defecto | **Nuevo: noviembre 2026** |

El diagrama 10 muestra todas las clases de las entregas anteriores en un solo dibujo, con la notación
UML completa (leyenda incluida en el diagrama). El 17 complementa al 10 con los patrones de la
Entrega Obligatoria N.º 2, y el 18 con los ajustes de noviembre de 2026.

## Regenerar los PNG

Requiere Java y Graphviz (`brew install graphviz` en macOS; en Windows, el instalador de graphviz.org):

```bash
curl -LO https://repo1.maven.org/maven2/net/sourceforge/plantuml/plantuml/1.2024.8/plantuml-1.2024.8.jar
java -jar plantuml-1.2024.8.jar -tpng -charset UTF-8 docs/diagramas/[0-9]*.puml
```

Sin Graphviz, PlantUML usa su motor de diagramado interno (así se generaron los diagramas de noviembre de
2026, en Windows): `java -jar plantuml-1.2024.8.jar -Playout=smetana -tpng -charset UTF-8 docs/diagramas/NN-*.puml`.

Para editarlos sin instalar nada: pegar el `.puml` en <https://www.plantuml.com/plantuml> (incluyendo el
contenido de `_estilo.iuml` en lugar de la línea `!include`).
