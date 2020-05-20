-- This file must be run before policies.sql
-- Statements/definitions must be separated by commented semicolon (--;) to allow simple parsing and execution of the file

-- Note: even though we limit access to this table by RLS policy here we might reference other schemas
-- and disclose that someone else has a schema with given URI.
CREATE TABLE IF NOT EXISTS run_schemas AS (
   WITH rs AS (
      SELECT id, owner, access, token, testid, '$' as prefix,
             jsonb_path_query(RUN.data, '$.\$schema'::jsonpath)#>>'{}' as uri
      FROM run
      UNION
      SELECT id, owner, access, token, testid, '$.*' as prefix,
             jsonb_path_query(RUN.data, '$.*.\$schema'::jsonpath)#>>'{}' as uri
      FROM run
   )
   SELECT rs.id as runid, rs.testid, schema.uri, schema.id as schemaid, prefix, rs.owner, rs.access, rs.token
   FROM rs INNER JOIN schema ON rs.uri = schema.uri
);

--;
CREATE OR REPLACE FUNCTION before_run_delete_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM run_schemas WHERE runid = OLD.id;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;

--;
CREATE OR REPLACE FUNCTION before_run_update_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM run_schemas WHERE runid = OLD.id;
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;

--;
CREATE OR REPLACE FUNCTION after_run_update_func() RETURNS TRIGGER AS $$
DECLARE
   v_schema text;
   v_schemaid integer;
BEGIN
   FOR v_schema IN (SELECT jsonb_path_query(NEW.data, '$.\$schema'::jsonpath)#>>'{}') LOOP
      v_schemaid := (SELECT id FROM schema WHERE uri = v_schema);
      IF v_schemaid IS NOT NULL THEN
         INSERT INTO run_schemas (runid, testid, prefix, uri, schemaid, owner, access, token)
         VALUES (NEW.id, NEW.testid, '$', v_schema, v_schemaid, NEW.owner, NEW.access, NEW.token);
      END IF;
   END LOOP;
   FOR v_schema IN (SELECT jsonb_path_query(NEW.data, '$.*.\$schema'::jsonpath)#>>'{}') LOOP
      v_schemaid := (SELECT id FROM schema WHERE uri = v_schema);
      IF v_schemaid IS NOT NULL THEN
         INSERT INTO run_schemas (runid, testid, prefix, uri, schemaid, owner, access, token)
         VALUES (NEW.id, NEW.testid, '$.*', v_schema, v_schemaid, NEW.owner, NEW.access, NEW.token);
      END IF;
   END LOOP;
   RETURN NULL;
END;
$$ LANGUAGE plpgsql;

--;
CREATE OR REPLACE FUNCTION before_schema_delete_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM run_schemas WHERE schemaid = OLD.id;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION before_schema_update_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM run_schemas WHERE schemaid = OLD.id;
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;

--;
CREATE OR REPLACE FUNCTION after_schema_update_func() RETURNS TRIGGER AS $$
BEGIN
   WITH rs AS (
      SELECT id, owner, access, token, testid, '$' as prefix,
             jsonb_path_query(RUN.data, '$.\$schema'::jsonpath)#>>'{}' as uri
      FROM run
      UNION
      SELECT id, owner, access, token, testid, '$.*' as prefix,
             jsonb_path_query(RUN.data, '$.*.\$schema'::jsonpath)#>>'{}' as uri
      FROM run
   )
   INSERT INTO run_schemas
   SELECT rs.id as runid, rs.testid, rs.uri, NEW.id as schemaid, prefix, owner, access, token
   FROM rs WHERE rs.uri = NEW.uri;
   RETURN NULL;
END;
$$ LANGUAGE plpgsql;

--;
DROP TRIGGER IF EXISTS before_run_delete ON run;
--;
DROP TRIGGER IF EXISTS before_run_update ON run;
--;
DROP TRIGGER IF EXISTS after_run_update ON run;
--;
DROP TRIGGER IF EXISTS before_schema_delete ON run;
--;
DROP TRIGGER IF EXISTS before_schema_update ON run;
--;
DROP TRIGGER IF EXISTS after_schema_update ON run;
--;
CREATE TRIGGER before_run_delete BEFORE DELETE ON run FOR EACH ROW EXECUTE FUNCTION before_run_delete_func();
--;
CREATE TRIGGER before_run_update BEFORE DELETE OR UPDATE ON run FOR EACH ROW EXECUTE FUNCTION before_run_update_func();
--;
CREATE TRIGGER after_run_update AFTER INSERT OR UPDATE ON run FOR EACH ROW EXECUTE FUNCTION after_run_update_func();
--;
CREATE TRIGGER before_schema_delete BEFORE DELETE ON schema FOR EACH ROW EXECUTE FUNCTION before_schema_delete_func();
--;
CREATE TRIGGER before_schema_update BEFORE UPDATE OF uri ON schema FOR EACH ROW EXECUTE FUNCTION before_schema_update_func();
--;
CREATE TRIGGER after_schema_update AFTER INSERT OR UPDATE OF uri ON schema FOR EACH ROW EXECUTE FUNCTION after_schema_update_func();