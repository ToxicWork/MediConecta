# ADR-0015: Singleton para el catálogo de prestaciones

- **Estado:** aceptado
- **Fecha:** 09/11/2026
- **Patrón:** Singleton

## Contexto

Cada cotización necesita el precio de la prestación. El catálogo cambia muy poco y se lee todo el tiempo.

## Decisión

`CatalogoPrestaciones` es un EJB `@Singleton @Startup`: el contenedor garantiza una sola instancia, la carga al desplegar desde `prestaciones.properties`, permite lecturas concurrentes (`LockType.READ`) y la recarga de madrugada.

## Alternativas descartadas

Singleton clásico con campo estático: no es seguro en un servidor con varios cargadores de clases ni maneja la concurrencia. Leer el archivo o la base en cada cotización: costo innecesario.

## Consecuencias

A favor: los precios se cambian sin recompilar. En contra: un cambio de precio se ve recién en la próxima recarga.

## Dónde está en el código

`negocio/catalogo/CatalogoPrestaciones`.

## Cómo se verifica

`CatalogoPrestacionesTest` (3).
