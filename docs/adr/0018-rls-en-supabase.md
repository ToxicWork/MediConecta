# ADR-0018: Row Level Security en las tablas de Supabase

- **Estado:** aceptado
- **Fecha:** 16/11/2026
- **Patrón:** Seguridad (defensa en profundidad)

## Contexto

La base está en Supabase, que publica el esquema `public` por una API REST propia. Las tablas que crea Hibernate nacen sin Row Level Security: con la clave anónima del proyecto se podían leer sin pasar por MediConecta. El asesor de seguridad de Supabase lo informaba como error crítico en 9 de las 13 tablas.

## Decisión

Se habilita RLS en todas las tablas de `public` sin definir políticas (`sql/02-habilitar-rls.sql`). Los roles de la API de Supabase no ven ninguna fila; MediConecta se conecta por JDBC con el rol dueño de las tablas, al que RLS no se aplica.

## Alternativas descartadas

Mover las tablas a otro esquema: obliga a reconfigurar JPA y el identity store. Escribir políticas por tabla: no hay ningún cliente legítimo de esa API, así que la política correcta es no dar acceso.

## Consecuencias

A favor: la única puerta a los datos es la aplicación, con sus roles. En contra: hay que volver a correr el script cuando una versión agrega entidades, porque Hibernate crea las tablas nuevas sin RLS.

## Dónde está en el código

`sql/02-habilitar-rls.sql`.

## Cómo se verifica

Consulta a `pg_tables` el 14/11/2026: 13 de 13 tablas con RLS; las pruebas de navegador se corrieron después de habilitarlo y aprobaron todas.
