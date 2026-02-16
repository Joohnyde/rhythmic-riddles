DO $$
DECLARE
  db_owner name;
  stmt text;
BEGIN
  -- 1) Determine owner of current database
  SELECT r.rolname
    INTO db_owner
  FROM pg_database d
  JOIN pg_roles r ON r.oid = d.datdba
  WHERE d.datname = current_database();

  -- 2) Schema owner
  EXECUTE format('ALTER SCHEMA public OWNER TO %I', db_owner);

  -- 3) Tables (ordinary + partitioned)
  FOR stmt IN
    SELECT format('ALTER TABLE %s OWNER TO %I;', c.oid::regclass, db_owner)
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind IN ('r','p')
  LOOP
    EXECUTE stmt;
  END LOOP;

  -- 4) Sequences
  FOR stmt IN
    SELECT format('ALTER SEQUENCE %s OWNER TO %I;', c.oid::regclass, db_owner)
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'S'
  LOOP
    EXECUTE stmt;
  END LOOP;

  -- 5) Views + materialized views
  FOR stmt IN
    SELECT format(
      'ALTER %s %s OWNER TO %I;',
      CASE c.relkind WHEN 'm' THEN 'MATERIALIZED VIEW' ELSE 'VIEW' END,
      c.oid::regclass,
      db_owner
    )
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind IN ('v','m')
  LOOP
    EXECUTE stmt;
  END LOOP;

  -- 6) Functions + procedures
  FOR stmt IN
    SELECT format(
      'ALTER %s %s OWNER TO %I;',
      CASE p.prokind WHEN 'p' THEN 'PROCEDURE' ELSE 'FUNCTION' END,
      p.oid::regprocedure,
      db_owner
    )
    FROM pg_proc p
    JOIN pg_namespace n ON n.oid = p.pronamespace
    WHERE n.nspname = 'public'
  LOOP
    EXECUTE stmt;
  END LOOP;
END
$$;

