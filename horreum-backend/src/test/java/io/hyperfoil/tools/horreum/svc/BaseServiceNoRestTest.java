package io.hyperfoil.tools.horreum.svc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Run;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.data.Transformer;

public abstract class BaseServiceNoRestTest {

    protected static final String DEFAULT_USER = "user";
    protected static final String[] UPLOADER_ROLES = { "foo-team", "foo-uploader", "uploader" };
    protected static final String FOO_TEAM = "foo-team";
    protected static final String FOO_TESTER = "foo-tester";
    protected static final String FOO_UPLOADER = "foo-uploader";
    protected static final String BAR_TEAM = "bar-team";

    @Inject
    protected EntityManager em;

    protected Schema createSampleSchema(String name, String uri, String owner) {
        Schema schema = new Schema();
        schema.owner = owner;
        schema.name = name;
        schema.uri = uri;
        schema.schema = null;
        return schema;
    }

    protected Transformer createSampleTransformer(String name, Schema schema, String owner, String function,
            Extractor... paths) {
        Transformer transformer = new Transformer();
        transformer.name = name;
        transformer.extractors = new ArrayList<>();
        for (Extractor path : paths) {
            if (path != null) {
                transformer.extractors.add(path);
            }
        }
        transformer.owner = owner == null ? schema.owner : owner;
        transformer.access = Access.PUBLIC;
        transformer.schemaId = schema.id;
        transformer.schemaUri = schema.uri;
        transformer.schemaName = schema.name;
        transformer.function = function;
        transformer.targetSchemaUri = "uri:" + schema.name + "-post-function";
        return transformer;
    }

    protected Label createSampleLabel(String name, Schema schema, String owner, String function, Extractor... paths) {
        Label label = new Label();
        label.name = name;
        label.extractors = new ArrayList<>();
        for (Extractor path : paths) {
            if (path != null) {
                label.extractors.add(path);
            }
        }
        label.owner = owner == null ? schema.owner : owner;
        label.access = Access.PUBLIC;
        label.schemaId = schema.id;
        label.function = function;
        return label;
    }

    protected List<Test> createSomeSampleTests(int count, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            prefix = "my_test";
        }
        List<Test> tests = new ArrayList<>();
        for (int i = 0; i < count; i += 1) {
            tests.add(createSampleTest("%s_%d".formatted(prefix, i), null, null, i));
        }
        return tests;
    }

    protected Test createSampleTest(String name, String owner, String folder, Integer datastoreId) {
        Test test = new Test();
        test.name = name;
        test.description = "Bar";
        test.owner = owner == null ? FOO_TEAM : owner;
        test.transformers = new ArrayList<>();
        test.datastoreId = datastoreId;
        test.folder = folder == null ? "" : folder;
        return test;
    }

    protected Run createSampleRun(int testId, JsonNode runJson, String owner) {
        Instant instant = Instant.now();

        Run run = new Run();
        run.testid = testId;
        run.data = runJson;
        run.trashed = false;
        run.start = instant;
        run.stop = instant;
        run.owner = owner == null ? FOO_TEAM : owner;

        return run;
    }
}
