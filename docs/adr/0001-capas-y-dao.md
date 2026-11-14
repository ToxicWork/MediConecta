# ADR-0001: Arquitectura en capas con Data Access Object

- **Estado:** aceptado
- **Fecha:** 14/09/2026
- **Patrón:** DAO

## Contexto

Las reglas de turnos, usuarios e historia clínica no pueden depender de cómo se consulta la base. Si los servicios escribieran JPQL, cambiar una consulta obligaría a tocar la lógica de negocio y no se podría probar una regla sin base de datos.

## Decisión

Cada agregado tiene su DAO (12 en total, paquete `dao`), que es el único lugar con `EntityManager` y JPQL parametrizado. Los servicios (`negocio`) sólo conocen los DAO; las vistas JSF y la API REST (`web`, `api`) sólo conocen los servicios.

## Alternativas descartadas

Usar `EntityManager` directamente en los EJB: menos clases, pero mezcla persistencia con negocio y repite consultas. Repositorios genéricos: ocultan consultas que en este dominio son específicas (disponibilidad, superposición de turnos).

## Consecuencias

A favor: las consultas están en un solo lugar y con parámetros (sin concatenar texto: no hay inyección SQL). En contra: más clases y un nivel más de indirección para operaciones triviales.

## Dónde está en el código

`dao/TurnoDAO`, `dao/UsuarioDAO`, `dao/HistoriaClinicaDAO` y los otros nueve DAO.

## Cómo se verifica

Pruebas de navegador `prueba_e2e.py` y `prueba_entrega4.py`: todo dato que se ve en pantalla pasó por un DAO.
