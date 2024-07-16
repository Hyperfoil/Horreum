package io.hyperfoil.tools.horreum.exp.svc;

import io.hyperfoil.tools.horreum.api.exp.data.Test;
import io.hyperfoil.tools.horreum.api.exp.LabelService;
import io.hyperfoil.tools.horreum.api.exp.TestService;
import io.hyperfoil.tools.horreum.exp.data.ExtractorDao;
import io.hyperfoil.tools.horreum.exp.data.LabelDAO;
import io.hyperfoil.tools.horreum.exp.data.LabelReducerDao;
import io.hyperfoil.tools.horreum.exp.data.TestDao;
import io.hyperfoil.tools.horreum.exp.mapper.TestMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.svc.Roles;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import org.jboss.resteasy.reactive.Separator;

import java.util.List;


@ApplicationScoped
public class TestServiceImpl implements TestService {

    @Inject
    LabelServiceImpl service;

    @Transactional
    @WithRoles
//    @RolesAllowed({Roles.TESTER})
    @PermitAll
    public void create(Test t) {
        TestMapper.to(t).persist();
    }

    @Transactional
    @WithRoles
//    @RolesAllowed({Roles.VIEWER})
    @PermitAll
    public Test getById(@PathParam("id") int testId) {
        return TestMapper.from(TestDao.findById(testId));
    }

    public TestDao getByName(String name) {
        return TestDao.find("from TestDao t where t.name =?1", name).firstResult();
    }


    @GET
    @Path("rhivos")
    @Transactional
    @WithRoles
//    @RolesAllowed({Roles.UPLOADER})
    @PermitAll
    public Test createRhivos() {
        TestDao t = new TestDao("rhivos-perf-comprehensive");
        String transformName = "transform";
        String transformPrefix = transformName + ExtractorDao.FOR_EACH_SUFFIX + ExtractorDao.NAME_SEPARATOR;
        t.loadLabels(
                new LabelDAO(transformName, t)
                        .loadExtractors(
                                ExtractorDao.fromString("$.user").setName("user"),
                                ExtractorDao.fromString("$.uuid").setName("uuid"),
                                ExtractorDao.fromString("$.run_id").setName("run_id"),
                                ExtractorDao.fromString("$.start_time").setName("start_time"),
                                ExtractorDao.fromString("$.end_time").setName("end_time"),
                                ExtractorDao.fromString("$.description").setName("description"),
                                ExtractorDao.fromString("$.ansible_facts").setName("ansible_facts"),
                                ExtractorDao.fromString("$.stressng_workload[*].test_results.test_config.stressors[0].workers").setName("workers"),
                                ExtractorDao.fromString("$.stressng_workload[*].test_results.test_config.stressors[0].stressor").setName("stressor"),
                                ExtractorDao.fromString("$.stressng_workload.pcp_time_series").setName("stressng_pcp_ts"),
                                ExtractorDao.fromString("$.stressng_workload[*].sample_uuid").setName("stressng_sample_uuid"),
                                ExtractorDao.fromString("$.coremark_pro_workload.pcp_time_series").setName("coremark_pro_pcp_ts"),
                                ExtractorDao.fromString("$.coremark_pro_workload[*].sample_uuid").setName("coremark_pro_sample_uuid"),
                                ExtractorDao.fromString("$.autobench_workload.pcp_time_series").setName("autobench_pcp_ts"),
                                ExtractorDao.fromString("$.autobench_workload[*].sample_uuid").setName("autobench_sample_uuid"),
                                ExtractorDao.fromString("$.stressng_workload[*].test_results").setName("stressng_results"),
                                ExtractorDao.fromString("$.coremark_pro_workload[*].results").setName("coremark_pro_results"),
                                ExtractorDao.fromString("$.autobench_workload[*].results").setName("autobench_results")
                        )
                        .setTargetSchema("urn:rhivos-perf-comprehensive-datasets:01")
                        .setReducer(new LabelReducerDao(
                                """
                                        ({
                                            stressng_sample_uuid, coremark_pro_sample_uuid, autobench_sample_uuid,
                                            stressng_results, coremark_pro_results, autobench_results,
                                            stressng_pcp_ts, coremark_pro_pcp_ts, autobench_pcp_ts,
                                            user, uuid, run_id, start_time, end_time, description, ansible_facts, workers, stressor
                                        }) => {
                                            var sngmap = stressng_sample_uuid.map((value, i) => ({
                                                sample_uuid: value,
                                                workload: "stressng",
                                                metadata: {user, uuid, run_id, start_time, end_time, description, ansible_facts},
                                                workers: workers[i],
                                                stressor: stressor[i],
                                                results: stressng_results[i],
                                                pcp_ts: stressng_pcp_ts[i]
                                            }));
                                            var cmpmap = coremark_pro_sample_uuid.map((value, i) => ({
                                                sample_uuid: value,
                                                workload: "coremark_pro",
                                                metadata: {user, uuid, run_id, start_time, end_time, description, ansible_facts},
                                                workers: coremark_pro_results[i]["coremark_pro_params"]["workers"],
                                                contexts: coremark_pro_results[i]["coremark_pro_params"]["contexts"],
                                                results: coremark_pro_results[i]["coremark_pro_results"],
                                                pcp_ts: coremark_pro_pcp_ts[i]
                                            }));
                                            var abmap = autobench_sample_uuid.map((value, i) => ({
                                                sample_uuid: value,
                                                workload: "autobench",
                                                metadata: {user, uuid, run_id, start_time, end_time, description, ansible_facts},
                                                workers: autobench_results[i]["autobench_params"]["workers"],
                                                contexts: autobench_results[i]["autobench_params"]["contexts"],
                                                results: autobench_results[i]["autobench_results"],
                                                pcp_ts: autobench_pcp_ts[i]
                                            }));
                                            const mymap = sngmap.concat(cmpmap, abmap);
                                            return mymap;
                                        }
                                        """
                        )),
                new LabelDAO("Autobench Multi Core", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.results.*.MultiCore").setName("results"),
                                ExtractorDao.fromString(transformPrefix + "$.workload").setName("workload")
                        )
                        .setReducer(new LabelReducerDao(
                                """
                                        value => {
                                            if (value["workload"] != "autobench") {
                                                return null
                                            }
                                            if(!value["results"]) {
                                                return 0
                                            } else {
                                                return parseFloat(((value["results"].reduce((a,b) => a+b, 0))/value["results"].length).toFixed(3))
                                            }
                                        };
                                        """
                        )),
                new LabelDAO("Autobench Scaling", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.results.*.Scaling").setName("results"),
                                ExtractorDao.fromString(transformPrefix + "$.workload").setName("workload")
                        ).setReducer(new LabelReducerDao(
                                """
                                        value => {
                                            if (value["workload"] != "autobench") {
                                                return null
                                            }
                                            if(!value["results"]) {
                                                return 0
                                            } else {\s
                                                return parseFloat(((value["results"].reduce((a,b) => a+b, 0))/value["results"].length).toFixed(3))
                                            }
                                        };
                                        """
                        )),
                new LabelDAO("Autobench Single Core", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.results.*.SingleCore").setName("results"),
                                ExtractorDao.fromString(transformPrefix + "$.workload").setName("workload")
                        ).setReducer(new LabelReducerDao(
                                """
                                        value => {
                                            if (value["workload"] != "autobench") {
                                                return null
                                            }
                                            if(!value["results"]) {
                                                return 0
                                            } else {\s
                                                return parseFloat(((value["results"].reduce((a,b) => a+b, 0))/value["results"].length).toFixed(3))
                                            }
                                        };
                                        """
                        )),
                new LabelDAO("Contexts", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.contexts").setName("contexts")
                        ),
                new LabelDAO("CoreMark-PRO Multi Core", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.results.\"CoreMark-PRO\".MultiCore").setName("coremark-pro-multi-core")
                        ),
                new LabelDAO("CoreMark-PRO Scaling", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.results.\"CoreMark-PRO\".Scaling").setName("coremark-pro-scaling")
                        ),
                new LabelDAO("CoreMark-PRO Single Core", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.results.\"CoreMark-PRO\".SingleCore").setName("coremark-pro-single-core")
                        ),
                new LabelDAO("Description", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata.description").setName("Description")
                        ),
                new LabelDAO("Hostname", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata.ansible_facts.env.HOSTNAME").setName("Hostname")
                        ),
                new LabelDAO("Kernel", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata.ansible_facts.kernel").setName("kernel")
                        ),
                new LabelDAO("Metadata", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata").setName("metadata")
                        ),
                new LabelDAO("PCP Time Series", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.pcp_ts").setName("pcp_time_series")
                        ),
                new LabelDAO("Results", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.results").setName("results")
                        ),
                new LabelDAO("RHIVOS Config", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata.rhivos_config").setName("RHIVOS Config")
                        ),
                new LabelDAO("Run ID", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata.run_id").setName("run_id")
                        ),
                new LabelDAO("Sample UUID", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.sample_uuid").setName("sample_uuid")
                        ),
                new LabelDAO("Start Time", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata.start_time").setName("start_time")
                        ),
                new LabelDAO("Stressor", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.stressor").setName("stressor")
                        ),
                new LabelDAO("User", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata.user").setName("user")
                        ),
                new LabelDAO("UUID", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.metadata.uuid").setName("uuid")
                        ),
                new LabelDAO("Workers", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.workers").setName("workers")
                        ),
                new LabelDAO("Workload", t)
                        .loadExtractors(
                                ExtractorDao.fromString(transformPrefix + "$.workload").setName("workload")
                        )
        );
        t.persist();
        return TestMapper.from(t);
    }

    @Transactional
    @WithRoles
//    @RolesAllowed({Roles.TESTER, Roles.VIEWER})
    @PermitAll
    public List<LabelService.ValueMap> labelValues(
            @PathParam("id") int testId,
            @QueryParam("group") String group,
            @QueryParam("filter") @DefaultValue("{}") String filter,
            @QueryParam("before") @DefaultValue("") String before,
            @QueryParam("after") @DefaultValue("") String after,
            @QueryParam("filtering") @DefaultValue("true") boolean filtering,
            @QueryParam("metrics") @DefaultValue("true") boolean metrics,
            @QueryParam("sort") @DefaultValue("") String sort,
            @QueryParam("direction") @DefaultValue("Ascending") String direction,
            @QueryParam("limit") @DefaultValue("" + Integer.MAX_VALUE) int limit,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("include") @Separator(",") List<String> include,
            @QueryParam("exclude") @Separator(",") List<String> exclude,
            @QueryParam("multiFilter") @DefaultValue("false") boolean multiFilter) {
        if (group != null && !group.isBlank()) {
            //TODO call labelValues with schema
            return service.labelValues(group, testId, include, exclude);
        } else {
            return service.labelValues(testId, filter, before, after, sort, direction, limit, page, include, exclude, multiFilter);
        }
    }
}
