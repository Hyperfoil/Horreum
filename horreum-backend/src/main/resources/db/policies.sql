-- Policies on table run
ALTER TABLE run ENABLE ROW LEVEL SECURITY;
CREATE POLICY run_select ON run FOR SELECT
    USING (can_view(access, owner, token));
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
    USING (can_view(access, owner, token));
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
    USING (can_view(access, owner, token));
CREATE POLICY schema_insert ON schema FOR INSERT
    WITH CHECK (has_role(owner));
CREATE POLICY schema_update ON schema FOR UPDATE
    USING (has_role(owner) AND has_role('viewer'))
    WITH CHECK (has_role(owner) AND has_role('tester'));
CREATE POLICY schema_delete ON schema FOR DELETE
    USING (has_role(owner) AND has_role('tester'));

-- Policies on table hook
ALTER TABLE hook ENABLE ROW LEVEL SECURITY;
CREATE POLICY hook_policy ON hook FOR ALL USING (has_role('admin'));

ALTER TABLE schemaextractor ENABLE ROW LEVEL SECURITY;
CREATE POLICY se_select ON schemaextractor FOR SELECT
    USING (exists(
        SELECT 1 FROM schema
        WHERE schema.id = schema_id AND can_view(schema.access, schema.owner, schema.token)
    ));
CREATE POLICY se_insert ON schemaextractor FOR INSERT
    WITH CHECK (exists(
        SELECT 1 FROM schema
        WHERE schema.id = schema_id AND has_role(schema.owner)
    ));
CREATE POLICY se_update ON schemaextractor FOR UPDATE
    USING (has_role('viewer') AND exists(
        SELECT 1 FROM schema
        WHERE schema.id = schema_id AND has_role(schema.owner)
    )) WITH CHECK (has_role('tester') AND exists(
        SELECT 1 FROM schema
        WHERE schema.id = schema_id AND has_role(schema.owner)
    ));
CREATE POLICY se_delete ON schemaextractor FOR DELETE
    USING (has_role('tester') AND exists(
        SELECT 1 FROM schema
        WHERE schema.id = schema_id AND has_role(schema.owner)
    ));

ALTER TABLE view ENABLE ROW LEVEL SECURITY;
CREATE POLICY view_select ON view FOR SELECT
    USING (exists(
        SELECT 1 FROM test
        WHERE test.id = test_id AND can_view(test.access, test.owner, test.token)
    ));
CREATE POLICY view_insert ON view FOR INSERT
    WITH CHECK (exists(
        SELECT 1 FROM test
        WHERE test.id = test_id AND has_role(test.owner)
    ));
CREATE POLICY view_update ON view FOR UPDATE
    USING (has_role('viewer') AND exists(
        SELECT 1 FROM test
        WHERE test.id = test_id AND has_role(test.owner)
    )) WITH CHECK (has_role('tester') AND exists(
        SELECT 1 FROM test
        WHERE test.id = test_id AND has_role(test.owner)
    ));
CREATE POLICY view_delete ON view FOR DELETE
    USING (has_role('tester') AND exists(
        SELECT 1 FROM test
        WHERE test.id = test_id AND has_role(test.owner)
    ));

ALTER TABLE viewcomponent ENABLE ROW LEVEL SECURITY;
CREATE POLICY vc_select ON viewcomponent FOR SELECT
    USING (exists(
        SELECT 1 FROM test
        JOIN view ON view.test_id = test.id
        WHERE view.id = view_id AND can_view(test.access, test.owner, test.token)
    ));
CREATE POLICY vc_insert ON viewcomponent FOR INSERT
    WITH CHECK (exists(
        SELECT 1 FROM test
        JOIN view ON view.test_id = test.id
        WHERE view.id = view_id AND has_role(test.owner)
    ));
CREATE POLICY vc_update ON viewcomponent FOR UPDATE
    USING (has_role('viewer') AND exists(
        SELECT 1 FROM test
        JOIN view ON view.test_id = test.id
        WHERE view.id = view_id AND has_role(test.owner)
    )) WITH CHECK (has_role('tester') AND exists(
        SELECT 1 FROM test
        JOIN view ON view.test_id = test.id
        WHERE view.id = view_id AND has_role(test.owner)
    ));
CREATE POLICY vc_delete ON viewcomponent FOR DELETE
    USING (has_role('tester') AND exists(
        SELECT 1 FROM test
        JOIN view ON view.test_id = test.id
        WHERE view.id = view_id AND has_role(test.owner)
    ));

-- Policies on the generated table run_schemas
ALTER TABLE run_schemas ENABLE ROW LEVEL SECURITY;
CREATE POLICY rs_select ON run_schemas FOR SELECT
    USING (exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND can_view(run.access, run.owner, run.token)
    ));
CREATE POLICY rs_insert ON run_schemas FOR INSERT
    WITH CHECK (exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND has_role(run.owner)
    ));
CREATE POLICY rs_update ON run_schemas FOR UPDATE
    USING (has_role('viewer') AND exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND has_role(run.owner)
    )) WITH CHECK (has_role('tester') AND exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND has_role(run.owner)
    ));
CREATE POLICY rs_delete ON run_schemas FOR DELETE
    USING (has_role('tester') AND exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND has_role(run.owner)
    ));

-- Policies on the generated table view_data
ALTER TABLE view_data ENABLE ROW LEVEL SECURITY;
CREATE POLICY vd_select ON view_data FOR SELECT
    USING (exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND can_view(run.access, run.owner, run.token)
    ));
CREATE POLICY vd_insert ON view_data FOR INSERT
    WITH CHECK (exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND has_role(run.owner)
    ));
CREATE POLICY vd_update ON view_data FOR UPDATE
    USING (has_role('viewer') AND exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND has_role(run.owner)
    ))
    WITH CHECK (has_role('tester') AND exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND has_role(run.owner)
    ));
CREATE POLICY vd_delete ON view_data FOR DELETE
    USING (has_role('tester') AND exists(
        SELECT 1 FROM run
        WHERE run.id = runid AND has_role(run.owner)
    ));
