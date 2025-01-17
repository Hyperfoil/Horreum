package io.hyperfoil.tools.horreum.exp.svc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.api.exp.LabelService;
import io.hyperfoil.tools.horreum.api.services.RunService;
import io.hyperfoil.tools.horreum.exp.data.LabelDao;
import io.hyperfoil.tools.horreum.exp.data.LabelGroupDao;
import io.hyperfoil.tools.horreum.exp.data.LabelValueDao;
import io.hyperfoil.tools.horreum.server.WithRoles;

/**
 * WARNING :: DO NOT merge into master
 *
 * This class exists as a test for the migraiton effort
 * it allows us to sanity check the migrated user schema and tests
 */
@Path("/api/x/migrate")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MigrateService {

    @Inject
    TestServiceImpl testService;

    @Inject
    LabelServiceImpl labelService;

    @Inject
    RunService legacyRunService;

    @Inject
    EntityManager em;

    @GET
    @Transactional
    @WithRoles
    @PermitAll
    @Path("group/{groupId}")
    public Response loadGroup(
            @PathParam("groupId") Long groupId

    ) {
        LabelGroupDao group = LabelGroupDao.findById(groupId);
        return Response.ok().entity(group).build();
    }

    /**
     * Load all LabelGroup entities to ensure they adhere to invariants
     * @return
     */
    @GET
    @Transactional
    @Path("groups")
    public Response loadAllGroups() {
        List<String> errors = new ArrayList<>();
        em.createNativeQuery("select id from exp_labelgroup", Integer.class).getResultStream().forEach(v -> {
            try {
                LabelGroupDao group = LabelGroupDao.findById(v);
            } catch (Exception e) {
                errors.add(v + " " + e.getMessage());
            }
        });
        return Response.ok().entity(errors).build();
    }

    @GET
    @Path("test/{testId}/targets")
    public JsonNode getTestTargets(
            @PathParam("testId") Long testId) {
        JsonNodeFactory nodeFactory = new ObjectMapper().getNodeFactory();
        ObjectNode rtrn = nodeFactory.objectNode();
        List<Object[]> results = em.createNativeQuery(
                "select distinct l.targetgroup_id,g.name from exp_label l left join exp_labelgroup g on l.targetgroup_id = g.id where l.group_id = ? and l.targetgroup_id is not null")
                .setParameter(1, testId).getResultList();

        for (Object[] row : results) {
            rtrn.set(row[0].toString(), nodeFactory.textNode(row[1].toString()));
        }
        return rtrn;
    }

    @GET
    @Path("test/{testId}/labelValues")
    public List<LabelService.ValueMap> testLabelValues(
            @PathParam("testId") Long testId

    ) {
        System.out.println("testid = " + testId);
        return labelService.labelValues(
                testId, "", "", "", "", "", Integer.MAX_VALUE, 0, Collections.EMPTY_LIST, Collections.EMPTY_LIST, false);
    }

    @GET
    @Path("test/{testId}/run/{runId}/labelValues")
    public List<LabelService.ValueMap> runLabelValues(
            @PathParam("testId") Long testId,
            @PathParam("runId") Long runId) {
        System.out.println("testId = " + testId + " runId = " + runId);
        return labelService.runLabelValues(
                testId, runId, "", "", "", "", "", Integer.MAX_VALUE, 0, Collections.EMPTY_LIST, Collections.EMPTY_LIST, false);
    }

    @GET
    @Path("test/{testId}/run/{runId}/label/{labelId}/labelValues")
    public List<LabelService.ValueMap> runLabelValuesByLabel(
            @PathParam("testId") Long testId,
            @PathParam("runId") Long runId,
            @PathParam("labelId") Long labelId) {
        System.out.println("testId = " + testId + " runId = " + runId + " labelId = " + labelId);
        return labelService.labelValues(labelId, runId, testId);
    }

    @GET
    @Path("test/{testId}/run/{runId}/label/{labelId}")
    public List<LabelValueDao> runLabelValue(
            @PathParam("testId") Long testId,
            @PathParam("runId") Long runId,
            @PathParam("labelId") Long labelId) {
        System.out.println("testId = " + testId + " runId = " + runId + " labelId = " + labelId);
        List<LabelValueDao> found = LabelValueDao
                .find("from LabelValueDao L where L.run.id=?1 and L.label.id=?2", runId, labelId).list();
        return found;
    }

    @GET
    @Path("test/{testId}/group/")
    public List<Long> getTargetLabelGroups(
            @PathParam("testId") Long testId) {
        System.out.println("testId = " + testId);
cha        List<Long> found = em.createNativeQuery("select targetgroup_id from exp_label where group_id=?1 and targetgroup_id is not null",Long.class)
                .setParameter(1, testId)
                .getResultList();
        return found;
    }


    @GET
    @Path("test/{testId}/group/{groupId}/labelValues")
    public List<LabelService.ValueMap> runLabelValuesByLabelGroup(
            @PathParam("testId") Long testId,
            @PathParam("groupId") Long groupId) {
        System.out.println("testId = " + testId + " groupId = " + groupId);
        LabelGroupDao group = LabelGroupDao.findById(groupId);
        return labelService.labelValues(group, testId, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
    }

    @GET
    @Path("test/{testId}/run/{runId}/label/{labelId}/extracted")
    public JsonNode calculateExtractedBlahBlah(
            @PathParam("testId") Long testId,
            @PathParam("runId") Long runId,
            @PathParam("labelId") Long labelId) {
        JsonNodeFactory nodeFactory = new ObjectMapper().getNodeFactory();
        LabelDao label = LabelDao.findById(labelId);
        if (label == null) {
            return nodeFactory.textNode("test " + testId + " does not have label " + labelId);
        }
        LabelServiceImpl.ExtractedValues evs = labelService.calculateExtractedValuesWithIterated(label, runId);
        return nodeFactory.textNode(evs.toString());
    }

    @GET
    @Path("test/{testId}/run/{runId}/calculate")
    public JsonNode calculateRunValues(
            @PathParam("testId") Long testId,
            @PathParam("runId") Long runId) {
        JsonNodeFactory nodeFactory = new ObjectMapper().getNodeFactory();
        ArrayNode rtrn = nodeFactory.arrayNode();
        LabelGroupDao labelGroup = LabelGroupDao.findById(testId);
        long start = System.currentTimeMillis();
        List<String> errors = labelService.calculateLabelValues(labelGroup.labels, runId);
        long stop = System.currentTimeMillis();
        System.out.println("    " + (stop - start) + "ms duration " + errors.size() + " errors");
        if (!errors.isEmpty()) {

            ObjectNode yikes = nodeFactory.objectNode();
            yikes.set("runId", nodeFactory.numberNode(runId));
            ArrayNode messages = nodeFactory.arrayNode();
            errors.forEach(messages::add);
            yikes.set("errors", messages);
            rtrn.add(yikes);
        }
        return rtrn;
    }

    @GET
    @Path("test/{testId}/calculate")
    public JsonNode calculateValues(
            @PathParam("testId") Long testId) {
        JsonNodeFactory nodeFactory = new ObjectMapper().getNodeFactory();
        ArrayNode rtrn = nodeFactory.arrayNode();

        LabelGroupDao labelGroup = LabelGroupDao.findById(testId);

        List<Long> runIds = em.createNativeQuery("select id from exp_run where test_id = ?", Long.class).setParameter(1, testId)
                .getResultList();
        System.out.println("testId " + testId + " run count " + runIds.size() + " labelCount " + labelGroup.labels.size());
        for (Long runId : runIds) {
            System.out.println("  " + runId);
            long start = System.currentTimeMillis();
            List<String> errors = labelService.calculateLabelValues(labelGroup.labels, runId);
            long stop = System.currentTimeMillis();
            System.out.println("    " + (stop - start) + "ms duration " + errors.size() + " errors");
            if (!errors.isEmpty()) {

                ObjectNode yikes = nodeFactory.objectNode();
                yikes.set("runId", nodeFactory.numberNode(runId));
                ArrayNode messages = nodeFactory.arrayNode();
                errors.forEach(messages::add);
                yikes.set("errors", messages);
                rtrn.add(yikes);
            }

            //            List<ExportedLabelValues> legacy = legacyRunService.labelValues(runId.intValue(), "{}", "", "Ascending",
            //                    Integer.MAX_VALUE, 0, Collections.EMPTY_LIST, Collections.EMPTY_LIST, false);
            //
            //            System.out.println("  legacy count = " + legacy.size());

        }

        return rtrn;
    }
    //Not transactional so we don't build one giant transaction

}
