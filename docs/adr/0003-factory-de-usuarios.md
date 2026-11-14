# ADR-0003: Factory para crear usuarios según su rol

- **Estado:** aceptado
- **Fecha:** 14/09/2026
- **Patrón:** Factory

## Contexto

Paciente, Profesional y Administrativo comparten datos pero cada uno exige campos propios (obra social; matrícula y especialidad). La creación se hacía con condicionales repartidos entre el registro y el alta administrativa.

## Decisión

`UsuarioFactory.crear(rol, datos, hash)` es el único lugar que instancia usuarios: elige la subclase según el rol, le pasa sólo los datos que le corresponden y normaliza los opcionales (un campo vacío queda sin valor).

## Alternativas descartadas

Constructores llamados desde cada servicio: repite la elección de la subclase en el registro y en el alta administrativa. Un único `Usuario` con campos opcionales y un atributo de tipo: permite estados inválidos (un paciente con matrícula).

## Consecuencias

A favor: agregar el rol Administrativo en noviembre fue una rama más en la fábrica y un test. En contra: la fábrica conoce a todas las subclases.

## Dónde está en el código

`negocio/factory/UsuarioFactory`, `negocio/factory/DatosRegistro`.

## Cómo se verifica

`UsuarioFactoryTest` (4 pruebas).
