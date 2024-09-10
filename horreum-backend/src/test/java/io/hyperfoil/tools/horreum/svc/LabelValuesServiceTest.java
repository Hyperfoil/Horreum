package io.hyperfoil.tools.horreum.svc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.test.HorreumTestProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

@QuarkusTest
@TestProfile(HorreumTestProfile.class)
class LabelValuesServiceTest extends BaseServiceNoRestTest {

    @Inject
    LabelValuesService labelValuesService;

    @Test
    void testGetFilterDefWithNoFilters() {
        LabelValuesService.FilterDef filterDef = labelValuesService.getFilterDef(
                null,
                null,
                null,
                false,
                true,
                s -> null);

        assertEquals("", filterDef.sql());
        assertNull(filterDef.simpleFilterObject());
        assertEquals(0, filterDef.multiFilterKeys().size());
        assertEquals(0, filterDef.multiFilterObject().size());
        assertEquals(0, filterDef.totalKeyChecks());
    }

    @Test
    void testGetFilterDefFromJsonpath() {
        LabelValuesService.FilterDef filterDef = labelValuesService.getFilterDef(
                JsonNodeFactory.instance.textNode("$.field"),
                null,
                null,
                false,
                true,
                s -> null);

        assertEquals("WHERE datasetId IN (SELECT inner_d.id\n" +
                "FROM dataset inner_d\n" +
                "    LEFT JOIN label_values inner_lv ON inner_d.id = inner_lv.dataset_id\n" +
                "    LEFT JOIN label inner_l ON inner_l.id = inner_lv.label_id\n" +
                "    WHERE inner_d.testid = :testId AND (inner_l.name = :jsonpathRoot AND inner_lv.value @\\?\\? CAST( :jsonpathFilter as jsonpath))\n"
                +
                "    GROUP BY inner_d.id\n" +
                "    HAVING COUNT(*) >= :filterKeysCount\n" +
                ") ", filterDef.sql());
        assertNull(filterDef.simpleFilterObject());
        assertEquals(0, filterDef.multiFilterKeys().size());
        assertEquals(0, filterDef.multiFilterObject().size());
        assertEquals(1, filterDef.totalKeyChecks());
    }

    @Test
    void testGetFilterDefWithBefore() {
        LabelValuesService.FilterDef filterDef = labelValuesService.getFilterDef(
                JsonNodeFactory.instance.textNode("$.field"),
                Instant.now(),
                null,
                false,
                true,
                s -> null);

        assertEquals("WHERE datasetId IN (SELECT inner_d.id\n" +
                "FROM dataset inner_d\n" +
                "    LEFT JOIN label_values inner_lv ON inner_d.id = inner_lv.dataset_id\n" +
                "    LEFT JOIN label inner_l ON inner_l.id = inner_lv.label_id\n" +
                "    WHERE inner_d.testid = :testId AND (inner_l.name = :jsonpathRoot AND inner_lv.value @\\?\\? CAST( :jsonpathFilter as jsonpath))\n"
                +
                "    GROUP BY inner_d.id\n" +
                "    HAVING COUNT(*) >= :filterKeysCount\n" +
                ")  AND  combined.stop < :before", filterDef.sql());
        assertNull(filterDef.simpleFilterObject());
        assertEquals(0, filterDef.multiFilterKeys().size());
        assertEquals(0, filterDef.multiFilterObject().size());
        assertEquals(1, filterDef.totalKeyChecks());
    }

    @Test
    void testGetFilterDefWithAfter() {
        LabelValuesService.FilterDef filterDef = labelValuesService.getFilterDef(
                null,
                null,
                Instant.now(),
                false,
                true,
                s -> null);

        assertEquals("WHERE  combined.start > :after", filterDef.sql());
        assertNull(filterDef.simpleFilterObject());
        assertEquals(0, filterDef.multiFilterKeys().size());
        assertEquals(0, filterDef.multiFilterObject().size());
        assertEquals(0, filterDef.totalKeyChecks());
    }

    @Test
    void testGetFilterDefWithBeforeAndAfter() {
        LabelValuesService.FilterDef filterDef = labelValuesService.getFilterDef(
                JsonNodeFactory.instance.textNode("$.field"),
                Instant.now(),
                Instant.now(),
                false,
                true,
                s -> null);

        assertEquals(
                "WHERE datasetId IN (SELECT inner_d.id\n" +
                        "FROM dataset inner_d\n" +
                        "    LEFT JOIN label_values inner_lv ON inner_d.id = inner_lv.dataset_id\n" +
                        "    LEFT JOIN label inner_l ON inner_l.id = inner_lv.label_id\n" +
                        "    WHERE inner_d.testid = :testId AND (inner_l.name = :jsonpathRoot AND inner_lv.value @\\?\\? CAST( :jsonpathFilter as jsonpath))\n"
                        +
                        "    GROUP BY inner_d.id\n" +
                        "    HAVING COUNT(*) >= :filterKeysCount\n" +
                        ")  AND  combined.stop < :before AND  combined.start > :after",
                filterDef.sql());
        assertNull(filterDef.simpleFilterObject());
        assertEquals(0, filterDef.multiFilterKeys().size());
        assertEquals(0, filterDef.multiFilterObject().size());
        assertEquals(1, filterDef.totalKeyChecks());
    }

    @Test
    void testGetFilterDefFromObject() {
        LabelValuesService.FilterDef filterDef = labelValuesService.getFilterDef(
                JsonNodeFactory.instance.objectNode().put("key", "value"),
                null,
                null,
                false,
                true,
                s -> null);

        assertEquals("WHERE datasetId IN (SELECT inner_d.id\n" +
                "FROM dataset inner_d\n" +
                "    LEFT JOIN label_values inner_lv ON inner_d.id = inner_lv.dataset_id\n" +
                "    LEFT JOIN label inner_l ON inner_l.id = inner_lv.label_id\n" +
                "    WHERE inner_d.testid = :testId AND ( (inner_l.name = :key0 AND inner_lv.value = :value0) )\n" +
                "    GROUP BY inner_d.id\n" +
                "    HAVING COUNT(*) >= :filterKeysCount\n" +
                ") ", filterDef.sql());
        assertEquals(JsonNodeType.OBJECT, filterDef.simpleFilterObject().getNodeType());
        assertEquals("value", filterDef.simpleFilterObject().get("key").asText());
        assertEquals(0, filterDef.multiFilterKeys().size());
        assertEquals(1, filterDef.simpleFilterObject().size());
        assertEquals(0, filterDef.multiFilterObject().size());
        assertNotNull(filterDef.simpleFilterObject().get("key"));
        assertEquals(1, filterDef.totalKeyChecks());
    }

    @Test
    void testGetFilterDefFromObjectWithMultiFilter() {
        ObjectNode filter = JsonNodeFactory.instance.objectNode().put("key1", "value1");
        filter.set("key2", JsonNodeFactory.instance.arrayNode().add("possible1").add("possible2"));
        filter.set("key3", JsonNodeFactory.instance.arrayNode().add("string").add(3));

        LabelValuesService.FilterDef filterDef = labelValuesService.getFilterDef(
                filter,
                null,
                null,
                true,
                true,
                s -> List.of());

        assertEquals(
                "WHERE datasetId IN (SELECT inner_d.id\n" +
                        "FROM dataset inner_d\n" +
                        "    LEFT JOIN label_values inner_lv ON inner_d.id = inner_lv.dataset_id\n" +
                        "    LEFT JOIN label inner_l ON inner_l.id = inner_lv.label_id\n" +
                        "    WHERE inner_d.testid = :testId AND ( (inner_l.name = :key0 AND inner_lv.value = :value0)  OR  (inner_l.name = :key1 AND inner_lv.value = ANY(:value1))  OR  (inner_l.name = :key2 AND inner_lv.value = ANY(:value2)) )\n"
                        +
                        "    GROUP BY inner_d.id\n" +
                        "    HAVING COUNT(*) >= :filterKeysCount\n" +
                        ") ",
                filterDef.sql());
        assertEquals(JsonNodeType.OBJECT, filterDef.simpleFilterObject().getNodeType());
        assertEquals("value1", filterDef.simpleFilterObject().get("key1").asText());
        assertNull(filterDef.simpleFilterObject().get("key2"));
        assertNull(filterDef.simpleFilterObject().get("key3"));
        assertEquals(2, filterDef.multiFilterKeys().size());
        assertArrayEquals(new String[] { "key2", "key3" }, filterDef.multiFilterKeys().toArray());
        assertEquals(2, filterDef.multiFilterObject().size());
        assertEquals(1, filterDef.simpleFilterObject().size());
        assertNotNull(filterDef.simpleFilterObject().get("key1"));
        assertEquals(3, filterDef.totalKeyChecks());
    }

    @Test
    void testGetFilterDefFromObjectWithMultiFilterAndBefore() {
        ObjectNode filter = JsonNodeFactory.instance.objectNode().put("key1", "value1");
        filter.set("key2", JsonNodeFactory.instance.arrayNode().add("possible1").add("possible2"));
        filter.set("key3", JsonNodeFactory.instance.arrayNode().add("string").add(3));

        LabelValuesService.FilterDef filterDef = labelValuesService.getFilterDef(
                filter,
                Instant.now(),
                null,
                true,
                true,
                s -> List.of());

        assertEquals(
                "WHERE datasetId IN (SELECT inner_d.id\n" +
                        "FROM dataset inner_d\n" +
                        "    LEFT JOIN label_values inner_lv ON inner_d.id = inner_lv.dataset_id\n" +
                        "    LEFT JOIN label inner_l ON inner_l.id = inner_lv.label_id\n" +
                        "    WHERE inner_d.testid = :testId AND ( (inner_l.name = :key0 AND inner_lv.value = :value0)  OR  (inner_l.name = :key1 AND inner_lv.value = ANY(:value1))  OR  (inner_l.name = :key2 AND inner_lv.value = ANY(:value2)) )\n"
                        +
                        "    GROUP BY inner_d.id\n" +
                        "    HAVING COUNT(*) >= :filterKeysCount\n" +
                        ")  AND  combined.stop < :before",
                filterDef.sql());
        assertEquals(JsonNodeType.OBJECT, filterDef.simpleFilterObject().getNodeType());
        assertEquals("value1", filterDef.simpleFilterObject().get("key1").asText());
        assertNull(filterDef.simpleFilterObject().get("key2"));
        assertNull(filterDef.simpleFilterObject().get("key3"));
        assertEquals(2, filterDef.multiFilterKeys().size());
        assertArrayEquals(new String[] { "key2", "key3" }, filterDef.multiFilterKeys().toArray());
        assertEquals(2, filterDef.multiFilterObject().size());
        assertEquals(1, filterDef.simpleFilterObject().size());
        assertNotNull(filterDef.simpleFilterObject().get("key1"));
        assertEquals(3, filterDef.totalKeyChecks());
    }
}
