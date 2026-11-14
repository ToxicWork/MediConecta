-- Habilita Row Level Security (RLS) en todas las tablas del esquema public.
--
-- Por qué: Supabase publica el esquema public por su API REST (PostgREST). Una tabla sin RLS se puede leer y
-- escribir con la clave anónima del proyecto, sin pasar por MediConecta: quedarían expuestos los usuarios
-- (con el hash de su contraseña) y la historia clínica. El asesor de seguridad de Supabase lo informa como
-- error crítico ("RLS Disabled in Public").
--
-- Con RLS habilitado y sin políticas, los roles anon y authenticated de esa API no ven ninguna fila.
-- MediConecta no cambia: se conecta por JDBC con el rol dueño de las tablas, al que RLS no se le aplica.
--
-- Hibernate crea las tablas nuevas sin RLS (hbm2ddl.auto=update): volver a correr este script después de
-- desplegar una versión que agregue entidades. Se puede correr todas las veces que haga falta.

DO $$
DECLARE
    tabla record;
BEGIN
    FOR tabla IN SELECT schemaname, tablename FROM pg_tables WHERE schemaname = 'public' AND NOT rowsecurity
    LOOP
        EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY', tabla.schemaname, tabla.tablename);
        RAISE NOTICE 'RLS habilitado en %', tabla.tablename;
    END LOOP;
END $$;

-- Verificación: no tiene que devolver filas.
-- SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND NOT rowsecurity;
