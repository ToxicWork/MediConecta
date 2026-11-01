-- Rol ADMINISTRATIVO (noviembre 2026).
--
-- Hibernate creó la tabla usuario con restricciones CHECK que sólo aceptan PACIENTE y PROFESIONAL en las
-- columnas rol y tipo_usuario (discriminador de la herencia SINGLE_TABLE). Con hbm2ddl.auto=update Hibernate
-- agrega tablas y columnas, pero no modifica restricciones existentes: sin este script, registrar un
-- administrativo falla con "violates check constraint usuario_rol_check".
--
-- Es aditivo: los usuarios existentes y las versiones anteriores de la app no se ven afectados.
-- Ejecutar una sola vez en la base (Supabase > SQL Editor). Se puede correr de nuevo sin problema.

BEGIN;

ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_rol_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_check
    CHECK (rol IN ('PACIENTE', 'PROFESIONAL', 'ADMINISTRATIVO'));

ALTER TABLE usuario DROP CONSTRAINT IF EXISTS usuario_tipo_usuario_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_tipo_usuario_check
    CHECK (tipo_usuario IN ('PACIENTE', 'PROFESIONAL', 'ADMINISTRATIVO'));

COMMIT;
