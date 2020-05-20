-- Install pgcrypto plugin
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;

-- Verify that what user has in repo.userroles is correctly signed
CREATE OR REPLACE FUNCTION has_role(owner TEXT) RETURNS boolean AS $$
DECLARE
    v_passphrase TEXT;
    v_userroles TEXT;
    v_role_salt_sign TEXT;
    v_parts TEXT[];
    v_role TEXT;
    v_salt TEXT;
    v_signature TEXT;
    v_computed TEXT;
BEGIN
    SELECT passphrase INTO v_passphrase FROM dbsecret;
    v_userroles := current_setting('repo.userroles', true);

    IF v_userroles = '' OR v_userroles IS NULL THEN
         RETURN 0;
    END IF;

    FOREACH v_role_salt_sign IN ARRAY regexp_split_to_array(v_userroles, ',')
    LOOP
        v_parts := regexp_split_to_array(v_role_salt_sign, ':');
        v_role := v_parts[1];
        IF v_role = owner THEN
            v_salt := v_parts[2];
            v_signature := v_parts[3];
            v_computed := encode(digest(v_role || v_salt || v_passphrase, 'sha256'), 'base64');
            IF v_computed = v_signature THEN
                RETURN 1;
            ELSE
                RAISE EXCEPTION 'invalid role + salt + signature';
            END IF;
        END IF;
    END LOOP;
    RETURN 0;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER STABLE;

-- Policies on table run
ALTER TABLE run ENABLE ROW LEVEL SECURITY;
CREATE POLICY run_select ON run FOR SELECT
    USING (
        access = 0
        OR (access = 1 AND has_role('viewer'))
        OR (access = 2 AND has_role(run.owner) AND has_role('viewer'))
        OR token = current_setting('repo.token', true)
    );
CREATE POLICY run_insert ON run FOR INSERT
    WITH CHECK (has_role(owner));
CREATE POLICY run_update ON run FOR UPDATE
    USING (has_role(owner) AND has_role('viewer')) WITH CHECK (has_role(owner));
CREATE POLICY run_delete ON run FOR DELETE
    USING (has_role(owner) AND has_role('tester'));

-- Policies on table test
ALTER TABLE test ENABLE ROW LEVEL SECURITY;
CREATE POLICY test_select ON test FOR SELECT
    USING (
        access = 0
        OR (access = 1 AND has_role('viewer'))
        OR (access = 2 AND has_role(owner) AND has_role('viewer'))
        OR token = current_setting('repo.token', true)
    );
CREATE POLICY test_insert ON test FOR INSERT
    WITH CHECK (has_role(owner));
CREATE POLICY test_update ON test FOR UPDATE
    USING (has_role(owner) AND has_role('viewer')) WITH CHECK (has_role(owner));
CREATE POLICY test_delete ON test FOR DELETE
    USING (has_role(owner) AND has_role('tester'));

 -- Policies on table schema
ALTER TABLE schema ENABLE ROW LEVEL SECURITY;
CREATE POLICY schema_select ON schema FOR SELECT
    USING (
        access = 0
        OR (access = 1 AND has_role('viewer'))
        OR (access = 2 AND has_role(owner) AND has_role('viewer'))
        OR token = current_setting('repo.token', true)
    );
CREATE POLICY schema_insert ON schema FOR INSERT
    WITH CHECK (has_role(owner));
CREATE POLICY schema_update ON schema FOR UPDATE
    USING (has_role(owner) AND has_role('viewer')) WITH CHECK (has_role(owner));
CREATE POLICY schema_delete ON schema FOR DELETE
    USING (has_role(owner) AND has_role('tester'));

-- Policies on table hook
ALTER TABLE hook ENABLE ROW LEVEL SECURITY;
CREATE POLICY hook_policy ON hook
    USING (has_role('admin')) WITH CHECK (has_role('admin'));

-- Policies on the generated table run_schemas
ALTER TABLE run_schemas ENABLE ROW LEVEL SECURITY;
CREATE POLICY rs_select ON run_schemas FOR SELECT
    USING (
        access = 0
        OR (access = 1 AND has_role('viewer'))
        OR (access = 2 AND has_role(run_schemas.owner) AND has_role('viewer'))
        OR token = current_setting('repo.token', true)
    );
CREATE POLICY rs_insert ON run_schemas FOR INSERT WITH CHECK (false);
CREATE POLICY rs_update ON run_schemas FOR UPDATE USING (false) WITH CHECK (false);
CREATE POLICY rs_delete ON run_schemas FOR DELETE USING (false);
