-- Policies on table run
ALTER TABLE run ENABLE ROW LEVEL SECURITY;
CREATE POLICY run_select ON run FOR SELECT
    USING (
        access = 0
        OR (access = 1 AND has_role('viewer'))
        OR (access = 2 AND has_role(run.owner) AND has_role('viewer'))
        OR token = current_setting('horreum.token', true)
    );
CREATE POLICY run_insert ON run FOR INSERT
    WITH CHECK (has_role(owner));
CREATE POLICY run_update ON run FOR UPDATE
    USING (has_role(owner) AND has_role('viewer'))
    WITH CHECK (has_role(owner) AND has_role('tester'));
CREATE POLICY run_delete ON run FOR DELETE
    USING (has_role(owner) AND has_role('tester'));

-- Policies on table test
ALTER TABLE test ENABLE ROW LEVEL SECURITY;
CREATE POLICY test_select ON test FOR SELECT
    USING (
        access = 0
        OR (access = 1 AND has_role('viewer'))
        OR (access = 2 AND has_role(owner) AND has_role('viewer'))
        OR token = current_setting('horreum.token', true)
    );
CREATE POLICY test_insert ON test FOR INSERT
    WITH CHECK (has_role(owner));
CREATE POLICY test_update ON test FOR UPDATE
    USING (has_role(owner) AND has_role('viewer'))
    WITH CHECK (has_role(owner) AND has_role('tester'));
CREATE POLICY test_delete ON test FOR DELETE
    USING (has_role(owner) AND has_role('tester'));

 -- Policies on table schema
ALTER TABLE schema ENABLE ROW LEVEL SECURITY;
CREATE POLICY schema_select ON schema FOR SELECT
    USING (
        access = 0
        OR (access = 1 AND has_role('viewer'))
        OR (access = 2 AND has_role(owner) AND has_role('viewer'))
        OR token = current_setting('horreum.token', true)
    );
CREATE POLICY schema_insert ON schema FOR INSERT
    WITH CHECK (has_role(owner));
CREATE POLICY schema_update ON schema FOR UPDATE
    USING (has_role(owner) AND has_role('viewer'))
    WITH CHECK (has_role(owner) AND has_role('tester'));
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
        OR token = current_setting('horreum.token', true)
    );
CREATE POLICY rs_insert ON run_schemas FOR INSERT
    WITH CHECK (has_role(owner));
CREATE POLICY rs_update ON run_schemas FOR UPDATE
    USING (has_role(owner) AND has_role('viewer'))
    WITH CHECK (has_role(owner) AND has_role('tester'));
CREATE POLICY rs_delete ON run_schemas FOR DELETE
    USING (has_role(owner) AND has_role('tester'));

-- Policies on the generated table view_data
ALTER TABLE view_data ENABLE ROW LEVEL SECURITY;
CREATE POLICY vd_select ON view_data FOR SELECT
    USING (
        access = 0
        OR (access = 1 AND has_role('viewer'))
        OR (access = 2 AND has_role(view_data.owner) AND has_role('viewer'))
        OR token = current_setting('horreum.token', true)
    );
CREATE POLICY vd_insert ON view_data FOR INSERT
   WITH CHECK (has_role(owner));
CREATE POLICY vd_update ON view_data FOR UPDATE
   USING (has_role(owner) AND has_role('viewer'))
   WITH CHECK (has_role(owner) AND has_role('tester'));
CREATE POLICY vd_delete ON view_data FOR DELETE
   USING (has_role(owner) AND has_role('tester'));
