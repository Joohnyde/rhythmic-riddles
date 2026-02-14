/*
  -------------------------------------------------------------------------
  Synchronize ownership of all objects in schema "public"
  with the owner of the current database.

  Why?
  - When restoring from a dump without ownership info,
    objects are owned by the role executing the script.
  - This block ensures everything ends up owned by
    the database owner instead.
  - No hardcoded role names.
  -------------------------------------------------------------------------
*/

DO $$
DECLARE
    db_owner name;   -- Will store the role name that owns this database
BEGIN

    ----------------------------------------------------------------------
    -- 1) Determine the owner of the CURRENT database
    --
    -- pg_database.datdba  = OID of owning role
    -- pg_roles.oid        = role identifier
    -- current_database()  = name of database we are connected to
    ----------------------------------------------------------------------
    SELECT r.rolname
      INTO db_owner
    FROM pg_database d
    JOIN pg_roles    r ON r.oid = d.datdba
    WHERE d.datname = current_database();


    ----------------------------------------------------------------------
    -- 2) Change owner of schema "public"
    --
    -- %I safely quotes identifiers (role names, schema names).
    ----------------------------------------------------------------------
    EXECUTE format(
        'ALTER SCHEMA public OWNER TO %I',
        db_owner
    );


    ----------------------------------------------------------------------
    -- 3) Change owner of all TABLES in schema "public"
    --
    -- relkind:
    --   'r' = ordinary table
    --   'p' = partitioned table
    --
    -- c.oid::regclass safely renders schema-qualified table name.
    ----------------------------------------------------------------------
    FOR
        EXECUTE
            SELECT format(
                'ALTER TABLE %s OWNER TO %I;',
                c.oid::regclass,
                db_owner
            )
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public'
              AND c.relkind IN ('r','p')
    LOOP
        -- Loop body intentionally empty.
        -- EXECUTE already runs each generated ALTER statement.
    END LOOP;


    ----------------------------------------------------------------------
    -- 4) Change owner of all SEQUENCES in schema "public"
    --
    -- relkind:
    --   'S' = sequence
    ----------------------------------------------------------------------
    FOR
        EXECUTE
            SELECT format(
                'ALTER SEQUENCE %s OWNER TO %I;',
                c.oid::regclass,
                db_owner
            )
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public'
              AND c.relkind = 'S'
    LOOP
    END LOOP;


    ----------------------------------------------------------------------
    -- 5) Change owner of VIEWS and MATERIALIZED VIEWS
    --
    -- relkind:
    --   'v' = view
    --   'm' = materialized view
    ----------------------------------------------------------------------
    FOR
        EXECUTE
            SELECT format(
                'ALTER %s %s OWNER TO %I;',
                CASE c.relkind
                    WHEN 'm' THEN 'MATERIALIZED VIEW'
                    ELSE 'VIEW'
                END,
                c.oid::regclass,
                db_owner
            )
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace
            WHERE n.nspname = 'public'
              AND c.relkind IN ('v','m')
    LOOP
    END LOOP;


    ----------------------------------------------------------------------
    -- 6) Change owner of FUNCTIONS and PROCEDURES
    --
    -- prokind:
    --   'f' = function
    --   'p' = procedure
    --
    -- oid::regprocedure safely formats function signature.
    ----------------------------------------------------------------------
    FOR
        EXECUTE
            SELECT format(
                'ALTER %s %s OWNER TO %I;',
                CASE p.prokind
                    WHEN 'p' THEN 'PROCEDURE'
                    ELSE 'FUNCTION'
                END,
                p.oid::regprocedure,
                db_owner
            )
            FROM pg_proc p
            JOIN pg_namespace n ON n.oid = p.pronamespace
            WHERE n.nspname = 'public'
    LOOP
    END LOOP;

END
$$;

