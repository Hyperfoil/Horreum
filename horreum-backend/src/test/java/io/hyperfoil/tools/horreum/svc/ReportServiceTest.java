package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.Test;
import io.hyperfoil.tools.horreum.api.report.ReportComment;
import io.hyperfoil.tools.horreum.api.report.TableReport;
import io.hyperfoil.tools.horreum.api.report.TableReportConfig;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.hyperfoil.tools.horreum.test.PostgresResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.oidc.server.OidcWiremockTestResource;

@QuarkusTest
@QuarkusTestResource(PostgresResource.class)
@QuarkusTestResource(OidcWiremockTestResource.class)
@TestProfile(HorreumTestProfile.class)
public class ReportServiceTest extends BaseServiceTest {

    @org.junit.jupiter.api.Test
    public void testNoFilter() throws InterruptedException {
        Test test = createTest(createExampleTest("nofilter"));
        createComparisonSchema();
        uploadExampleRuns(test);

        TableReportConfig config = newExampleTableReportConfig(test);
        TableReport report = jsonRequest().body(config).post("/api/report/table/config")
                .then().statusCode(200).extract().body().as(TableReport.class);

        assertEquals(8, report.data.size());
        assertCount(report, 4, d -> d.category, "jvm");
        assertCount(report, 4, d -> d.category, "native");
        assertCount(report, 4, d -> d.series, "windows");
        assertCount(report, 4, d -> d.series, "linux");
        TableReport.DataDTO duplicated = report.data.stream()
                .filter(d -> "windows".equals(d.series) && "jvm".equals(d.category) && Integer.parseInt(d.scale) == 2)
                .findFirst().orElseThrow();
        assertEquals(0.4, duplicated.values.get(0).asDouble());
        assertEquals(120_000_000L, duplicated.values.get(1).asLong());
        assertEquals(256, duplicated.values.get(2).asInt());

        deleteReport(report);
    }

    @org.junit.jupiter.api.Test
    public void testFilter() throws InterruptedException {
        Test test = createTest(createExampleTest("filter"));
        createComparisonSchema();
        uploadExampleRuns(test);

        TableReportConfig config = newExampleTableReportConfig(test);
        config.filterLabels = arrayOf("variant");
        config.filterFunction = "v => v === 'production'";
        TableReport report = jsonRequest().body(config).post("/api/report/table/config")
                .then().statusCode(200).extract().body().as(TableReport.class);

        assertEquals(6, report.data.size());
        assertCount(report, 3, d -> d.category, "jvm");
        assertCount(report, 3, d -> d.category, "native");
        assertCount(report, 4, d -> d.series, "windows");
        assertCount(report, 2, d -> d.series, "linux");
        TableReport.DataDTO duplicated = report.data.stream()
                .filter(d -> "windows".equals(d.series) && "jvm".equals(d.category) && Integer.parseInt(d.scale) == 2)
                .findFirst().orElseThrow();
        assertEquals(0.8, duplicated.values.get(0).asDouble());
        assertEquals(150_000_000L, duplicated.values.get(1).asLong());
        assertEquals(200, duplicated.values.get(2).asInt());

        ReportComment comment = createComment(2, "native", "Hello world");
        ReportComment commentWithId = jsonRequest().body(comment).post("/api/report/comment/" + report.id)
                .then().statusCode(200).extract().body().as(ReportComment.class);
        TableReport updatedReport = jsonRequest().get("/api/report/table/" + report.id)
                .then().statusCode(200).extract().body().as(TableReport.class);
        assertEquals(1, updatedReport.comments.size());
        ReportComment readComment = updatedReport.comments.stream().findFirst().orElseThrow();
        assertEquals(commentWithId.id, readComment.id);
        assertEquals(2, readComment.level);
        assertEquals("native", readComment.category);
        assertEquals("Hello world", readComment.comment);

        commentWithId.comment = "Nazdar";
        jsonRequest().body(commentWithId).post("/api/report/comment/" + report.id);
        comment.comment = "Ahoj";
        comment.category = "jvm";
        jsonRequest().body(comment).post("/api/report/comment/" + report.id); // no comment ID => add

        updatedReport = jsonRequest().get("/api/report/table/" + report.id)
                .then().statusCode(200).extract().body().as(TableReport.class);
        assertEquals(2, updatedReport.comments.size());
        assertEquals(1, updatedReport.comments.stream().filter(c -> "Nazdar".equals(c.comment)).count());
        assertEquals(1, updatedReport.comments.stream().filter(c -> "Ahoj".equals(c.comment)).count());

        deleteReport(report);
    }

    private ReportComment createComment(int level, String category, String msg) {
        ReportComment comment = new ReportComment();
        comment.level = 2;
        comment.category = category;
        comment.componentId = 1;
        comment.comment = msg;
        return comment;
    }

    private void deleteReport(TableReport report) {
        jsonRequest().delete("/api/report/table/" + report.id).then().statusCode(204);
    }

    private void assertCount(TableReport report, int expected, Function<TableReport.DataDTO, String> selector, String value) {
        assertEquals(expected, report.data.stream().map(selector).filter(value::equals).count());
    }

    @org.junit.jupiter.api.Test
    public void testMissingValues() throws InterruptedException {
        Test test = createTest(createExampleTest("missing"));
        createComparisonSchema();

        BlockingQueue<Dataset.LabelsUpdatedEvent> queue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        int runId = uploadRun(JsonNodeFactory.instance.objectNode(), test.name);
        assertNotNull(queue.poll(10, TimeUnit.SECONDS));

        TableReportConfig config = newExampleTableReportConfig(test);
        TableReport report = jsonRequest().body(config).post("/api/report/table/config")
                .then().statusCode(200).extract().body().as(TableReport.class);

        assertEquals(1, report.data.size());
        TableReport.DataDTO data = report.data.iterator().next();
        assertEquals(runId, data.runId);
        assertEquals("", data.series);
        assertEquals("", data.category);
        assertEquals("", data.scale);
        assertEquals(3, data.values.size());
        data.values.forEach(value -> assertTrue(value.isNull()));
    }

    @org.junit.jupiter.api.Test
    public void testUndefinedComponent() throws InterruptedException {
        Test test = createTest(createExampleTest("previewMissingComponent"));
        createComparisonSchema();

        BlockingQueue<Dataset.LabelsUpdatedEvent> queue = serviceMediator
                .getEventQueue(AsyncEventChannels.DATASET_UPDATED_LABELS, test.id);
        int runId = uploadRun(JsonNodeFactory.instance.objectNode(), test.name);
        assertNotNull(queue.poll(10, TimeUnit.SECONDS));

        TableReportConfig config = newExampleTableReportConfig(test);
        TableReport report = jsonRequest().body(config).post("/api/report/table/config")
                .then().statusCode(200).extract().body().as(TableReport.class);

        TableReport preview = jsonRequest().body(config).post("/api/report/table/preview?edit=" + report.id)
                .then().statusCode(200).extract().body().as(TableReport.class);
        assertNotNull(preview);
        assertNotNull(preview.config.components);

        preview = jsonRequest().body(config).post("/api/report/table/preview")
                .then().statusCode(200).extract().body().as(TableReport.class);
        assertNotNull(preview);
        assertNotEquals(0, preview.config.components.size());
    }

}
