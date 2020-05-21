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
CREATE TRIGGER before_run_update BEFORE UPDATE ON run FOR EACH ROW EXECUTE FUNCTION before_run_update_func();
--;
CREATE TRIGGER after_run_update AFTER INSERT OR UPDATE ON run FOR EACH ROW EXECUTE FUNCTION after_run_update_func();
--;
CREATE TRIGGER before_schema_delete BEFORE DELETE ON schema FOR EACH ROW EXECUTE FUNCTION before_schema_delete_func();
--;
CREATE TRIGGER before_schema_update BEFORE UPDATE OF uri ON schema FOR EACH ROW EXECUTE FUNCTION before_schema_update_func();
--;
CREATE TRIGGER after_schema_update AFTER INSERT OR UPDATE OF uri ON schema FOR EACH ROW EXECUTE FUNCTION after_schema_update_func();

--;
CREATE TABLE IF NOT EXISTS view_data AS (
   WITH vcs AS (
      SELECT id, unnest(regexp_split_to_array(accessors, ';')) as aa
      FROM viewcomponent
   )
   SELECT vcs.id as vcid, rs.runid, array_agg(se.id) as extractor_ids, run.owner, run.access, run.token,
      jsonb_object_agg(se.accessor, (CASE
      WHEN aa like '%[]' THEN jsonb_path_query_array(run.data, (rs.prefix || se.jsonpath)::jsonpath)
      ELSE jsonb_path_query_first(run.data, (rs.prefix || se.jsonpath)::jsonpath)
   END)) as object
   FROM vcs
   JOIN schemaextractor se ON se.accessor = replace(aa, '[]', '')
   JOIN run_schemas rs ON rs.schemaid = se.schema_id
   JOIN run on run.id = rs.runid
   GROUP BY runid, vcid, run.owner, run.access, run.token
);
--;
CREATE OR REPLACE FUNCTION vd_before_delete_run_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM view_data WHERE runid = OLD.runid;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;
--;
CREATE OR REPLACE FUNCTION vd_after_insert_run_func() RETURNS TRIGGER AS $$
BEGIN
   WITH vcs AS (
      SELECT id, unnest(regexp_split_to_array(accessors, ';')) as aa
      FROM viewcomponent
   )
   INSERT INTO view_data
   SELECT vcs.id as vcid, rs.runid, array_agg(se.id) as extractor_ids, run.owner, run.access, run.token,
      jsonb_object_agg(se.accessor, (CASE
      WHEN aa like '%[]' THEN jsonb_path_query_array(run.data, (rs.prefix || se.jsonpath)::jsonpath)
      ELSE jsonb_path_query_first(run.data, (rs.prefix || se.jsonpath)::jsonpath)
   END)) as object
   FROM vcs
   JOIN schemaextractor se ON se.accessor = replace(aa, '[]', '')
   JOIN run_schemas rs ON rs.schemaid = se.schema_id
   JOIN run on run.id = rs.runid
   WHERE run.id = NEW.runid
   GROUP BY runid, vcid, run.owner, run.access, run.token;
   RETURN NULL;
END;
$$ LANGUAGE plpgsql;

--;
CREATE OR REPLACE FUNCTION vd_before_delete_extractor_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM view_data WHERE OLD.id = ANY(extractor_ids);
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;
--;
CREATE OR REPLACE FUNCTION vd_before_update_extractor_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM view_data WHERE OLD.id = ANY(extractor_ids);
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;
--;
CREATE OR REPLACE FUNCTION vd_after_update_extractor_func() RETURNS TRIGGER AS $$
BEGIN
   WITH vcs AS (
      SELECT id, unnest(regexp_split_to_array(accessors, ';')) as aa
      FROM viewcomponent
   )
   INSERT INTO view_data
   SELECT vcs.id as vcid, rs.runid, array_agg(se.id) as extractor_ids, run.owner, run.access, run.token,
      jsonb_object_agg(se.accessor, (CASE
      WHEN aa like '%[]' THEN jsonb_path_query_array(run.data, (rs.prefix || se.jsonpath)::jsonpath)
      ELSE jsonb_path_query_first(run.data, (rs.prefix || se.jsonpath)::jsonpath)
   END)) as object
   FROM vcs
   JOIN schemaextractor se ON se.accessor = replace(aa, '[]', '')
   JOIN run_schemas rs ON rs.schemaid = se.schema_id
   JOIN run on run.id = rs.runid
   WHERE se.id = NEW.id
   GROUP BY runid, vcid, run.owner, run.access, run.token;
   RETURN NULL;
END;
$$ LANGUAGE plpgsql;
--;
CREATE OR REPLACE FUNCTION vd_before_delete_vc_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM view_data WHERE vcid = OLD.id;
   RETURN OLD;
END;
$$ LANGUAGE plpgsql;
--;
CREATE OR REPLACE FUNCTION vd_before_update_vc_func() RETURNS TRIGGER AS $$
BEGIN
   DELETE FROM view_data WHERE vcid = OLD.id;
   RETURN NEW;
END;
$$ LANGUAGE plpgsql;
--;
CREATE OR REPLACE FUNCTION vd_after_update_vc_func() RETURNS TRIGGER AS $$
BEGIN
   WITH vcs AS (
      SELECT id, unnest(regexp_split_to_array(accessors, ';')) as aa
      FROM viewcomponent
      WHERE id = NEW.id
   )
   INSERT INTO view_data
   SELECT vcs.id as vcid, rs.runid, array_agg(se.id) as extractor_ids, run.owner, run.access, run.token,
      jsonb_object_agg(se.accessor, (CASE
      WHEN aa like '%[]' THEN jsonb_path_query_array(run.data, (rs.prefix || se.jsonpath)::jsonpath)
      ELSE jsonb_path_query_first(run.data, (rs.prefix || se.jsonpath)::jsonpath)
   END)) as object
   FROM vcs
   JOIN schemaextractor se ON se.accessor = replace(aa, '[]', '')
   JOIN run_schemas rs ON rs.schemaid = se.schema_id
   JOIN run on run.id = rs.runid
   GROUP BY runid, vcid, run.owner, run.access, run.token;
   RETURN NULL;
END;
$$ LANGUAGE plpgsql;

--;
DROP TRIGGER IF EXISTS vd_before_delete ON run_schemas;
--;
DROP TRIGGER IF EXISTS vd_after_insert ON run_schemas;
--;
DROP TRIGGER IF EXISTS vd_before_delete ON schemaextractor;
--;
DROP TRIGGER IF EXISTS vd_before_update ON schemaextractor;
--;
DROP TRIGGER IF EXISTS vd_after_update ON schemaextractor;
--;
DROP TRIGGER IF EXISTS vd_before_delete ON viewcomponent;
--;
DROP TRIGGER IF EXISTS vd_before_update ON viewcomponent;
--;
DROP TRIGGER IF EXISTS vd_after_update ON viewcomponent;
--;

-- Run schemas are never updated, just deleted and inserted - we can have triggers only for these events.
-- In addition we don't have to listen on table `run` since any change will trigger run_schemas modification.
--;
CREATE TRIGGER vd_before_delete BEFORE DELETE ON run_schemas FOR EACH ROW EXECUTE FUNCTION vd_before_delete_run_func();
--;
CREATE TRIGGER vd_after_insert AFTER INSERT ON run_schemas FOR EACH ROW EXECUTE FUNCTION vd_after_insert_run_func();
--;
CREATE TRIGGER vd_before_delete BEFORE DELETE ON schemaextractor FOR EACH ROW EXECUTE FUNCTION vd_before_delete_extractor_func();
--;
CREATE TRIGGER vd_before_update BEFORE UPDATE ON schemaextractor FOR EACH ROW EXECUTE FUNCTION vd_before_update_extractor_func();
--;
CREATE TRIGGER vd_after_update AFTER INSERT OR UPDATE ON schemaextractor FOR EACH ROW EXECUTE FUNCTION vd_after_update_extractor_func();
--;
CREATE TRIGGER vd_before_delete BEFORE DELETE ON viewcomponent FOR EACH ROW EXECUTE FUNCTION vd_before_delete_vc_func();
--;
CREATE TRIGGER vd_before_update BEFORE UPDATE OF id, accessors ON viewcomponent FOR EACH ROW EXECUTE FUNCTION vd_before_update_vc_func();
--;
CREATE TRIGGER vd_after_update AFTER INSERT OR UPDATE OF id, accessors ON viewcomponent FOR EACH ROW EXECUTE FUNCTION vd_after_update_vc_func();
