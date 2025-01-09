package io.hyperfoil.tools.horreum.liquibase;

import static io.hyperfoil.tools.horreum.exp.data.ExtractorDao.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.hyperfoil.tools.horreum.exp.data.ExtractorDao;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * This is basically a duplicate of the logic in the Entities because it has to run before Hibernate initializes.
 * Pseudocode for what it does:
 * 1. Group all schemas based on their 'baseUri' where baseUri is the uri minus ':version' or 'vVersion'
 * and version is some combination of digits and dots (semver, decimal, integer, etc)
 * 2. Create a global Label Group for each group of schemas
 *     2.1. Group labels by name
 *       2.1.1 Labels that did not chang across all versions are added once to the global group
 *       2.1.2 Labels that changed are added with a new name reflecting the version where they occurred.
 *         e.g. a label cpu that was unchanged in version 1.0 and 2.0 but changed in version 3.0 would have two entries [cpu10, cpu20].
 *         A final label is added with the original name (e.g. cpu) that extracts the value form each of the previous labels [cpu10, cpu20]
 *         and returns the first value it encounters. This assumes old labels do not match in newer data. Users will have to change the new
 *         labels if the assumption is wrong.
 * 3. Build a list of all tests that use Transforms
 * 4. Build a list of all Transforms used by more than one Test
 * 5. For each test
 *   5.1. If the test uses a shared transform then create a GLOBAL label group with the transform as the only label (if it doesn't already exist).
 *     The name of the group is the label's name + "-group" at the moment.
 *   5.2 If the test uses a transform create a label for the transformation (referencing the global original if it exists)
 *     5.2.1 If the transformation had a target schema then "load" that group from this label.
 *           If it did NOT have a target schema then load the original schema that defined the transformation as the target. (Is this correct?)
 *   5.3 For each jsonpaths that end in '$schema' for each run in the test excluding
 *     5.3.1 If the path is '$."schema"' then import that group into the test's group.
 *           Otherwise, create a label with the specified extractor and load the target group. The target group.
 *           If there is more than 1 target group then the assumptions about global Schema grouping were not suited to their use case.
 *           This didn't happen in our production DB but would mean users have to manually create a suitable target group (for group management)
 *           or just merge the groups without tracking group references (which means they lose versioning). Option 1 sounds better.
 *   Do we do the same as 5.3 for datasets? It is theoretically possible for a user to have injected $schema into datasets that were not there
 *   in the original data. I'm not sure how horreum handles this.
 *
 */
public class ComposableMigration implements CustomTaskChange {

    @Override
    public void execute(Database database) throws CustomChangeException {
        if (database.getConnection() instanceof JdbcConnection) {
            Connection conn = ((JdbcConnection) database.getConnection()).getWrappedConnection();
            migrate(conn);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Labels and Transformers migrated to Composable Labels";
    }

    @Override
    public void setUp() throws SetupException {

    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {

    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }

    public static void migrate(Connection conn) {
        //this ensures testIds do not change when converted to labelgorups
        System.out.println("persisting tests");
        persistAllTests(conn);
        //make sure we do not get an id conflict
        updateLabelGroupSeq(conn);

        //this takes all schemas from Horreum and creates LabelGroups with owner = GLOBAL
        System.out.println("creating global schemas");
        //this isn't working correctly
        Map<String, Long> globalSchemas = createGlobalSchemas(conn);
        System.out.println("done creating global schemas");

        List<TestTransformsRef> transformsRefs = getTestTransforms(conn);
        Set<Long> sharedTransformIds = getSharedTransformIds(conn);

        System.out.println("creating shared groups");
        Map<Long, Label> transformParents = sharedTransformIds.stream().distinct().collect(Collectors.toMap(id -> id, id -> {
            LabelContext groupContext = new LabelContext();
            LabelDef def = fromTransform(id, conn);
            Long targetGroupId = globalSchemas.getOrDefault(def.target, null);
            LabelGroup sharedGroup = persistGroup(
                    new LabelGroup(null, "GLOBAL-" + def.name, "GLOBAL", "group", new ArrayList<>()), conn,
                    groupContext);
            Label persisted = persistLabelDef(def, sharedGroup.id, true, null, null, targetGroupId, null,
                    conn, groupContext);
            addTempTransformMap(id, persisted.id, conn);
            return persisted;
        }));
        //
        Map<Long, String> transformTargetUri = transformTargetUri(conn);
        //
        Set<Long> testsWithTransforms = transformsRefs.stream().map(ref -> ref.testId).collect(Collectors.toSet());

        Set<Long> testIds = getAllTestIds(conn);
        for (long testId : testIds) {
            System.out.println("migrating test: " + testId);
            //create a lookup context for the test and a new group
            LabelContext testContext = new LabelContext();
            LabelGroup testGroup = loadGroup(testId, conn);
            //if the test has transforms
            if (testsWithTransforms.contains(testId)) {
                TestTransformsRef ref = transformsRefs.stream().filter(r -> r.testId == testId).findFirst().orElse(null);
                assert ref != null;

                AtomicInteger counter = new AtomicInteger(1);
                List<LabelDef> labelDefs = ref.transforms.stream().map(id -> fromTransform(id, conn)).toList();
                boolean oneUniqueLabel = labelDefs.size() == 1 || allTheSameLabelDefs(labelDefs);
                boolean nameCollision = ref.transforms.stream().map(id -> fromTransform(id, conn))
                        .map(def -> def.name).collect(Collectors.toSet()).size() < ref.transforms.size();
                //useCounter if there is more than one unique Label and there are duplicate names
                boolean useCounter = !oneUniqueLabel && nameCollision;
                if (oneUniqueLabel) {
                    Long first = ref.transforms.get(0);
                    ref.transforms.clear();
                    ref.transforms.add(first);
                }
                //This is the start of the new logic (attempt)
                Map<Long, String> transformNames = ref.transforms.stream().collect(Collectors.toMap(id -> id, id -> {
                    LabelDef def = fromTransform(id, conn);
                    return def.name + (useCounter ? counter.getAndIncrement() : "");
                }));

                //                    List<LabelDef> transformLabels = ref.transforms.stream().map(id->{
                //                        LabelDef def = fromTransform(id, conn);
                //                        if (useCounter) {
                //                            def = new LabelDef(def.name + counter.getAndIncrement(), def.javascript, def.target,
                //                                    def.extractors);
                //                        }
                //                        return def;
                //                    }).toList();
                Map<Long, List<Long>> targetGroupIdToLabelId = ref.transforms.stream().collect(Collectors.toMap(
                        id -> {
                            String targetUri = transformTargetUri.get(id);
                            return globalSchemas.getOrDefault(targetUri, null);
                        }, id -> new ArrayList<>(Collections.singletonList(id)),
                        (a, b) -> {
                            a.addAll(b);
                            return a;
                        }));
                targetGroupIdToLabelId.forEach((targetGroupId, legacyTransformIds) -> {
                    //all of targetingDefs target the same new labelGroup
                    boolean moreThanOneDef = legacyTransformIds.size() > 1;
                    List<Label> persistedLabels = legacyTransformIds.stream().map(id -> {
                        Long originalLabelId = null;
                        Long sourceGroupId = null;
                        if (transformParents.containsKey(id)) {
                            Label ancestorLabel = transformParents.get(id);
                            originalLabelId = ancestorLabel.id;
                            sourceGroupId = ancestorLabel.groupId;
                        }
                        LabelDef def = fromTransform(id, conn);
                        def = new LabelDef(transformNames.get(id), def.javascript, def.target,
                                def.extractors);

                        Label persistedLabel = persistLabelDef(def, testGroup.id, true, null, sourceGroupId,
                                !moreThanOneDef ? targetGroupId : null,
                                originalLabelId, conn, testContext);
                        testGroup.labels.add(persistedLabel);
                        addTempTransformMap(id, persistedLabel.id, conn);
                        if (legacyTransformIds.size() == 1 && targetGroupId != null) {
                            //loading the targetGroup into testGroup, something is wrong with this
                            LabelGroup targetedGroup = loadGroup(targetGroupId, conn);
                            //This is a mistake, this should be done by a label that merges all the transforms not individually
                            List<Label> loadedTargetGroupLabels = persistedLabel.targetGroup(targetedGroup, testContext,
                                    conn);
                            testGroup.labels.addAll(loadedTargetGroupLabels);
                        }
                        return persistedLabel;
                    }).toList();
                    //
                    if (moreThanOneDef) {
                        LabelDef def = fromTransform(legacyTransformIds.get(0), conn);
                        String newName = def.name;
                        Set<String> usedNames = new HashSet<>(transformNames.values());
                        while (usedNames.contains(newName)) {
                            newName = newName + counter.getAndIncrement();
                        }

                        long reducerId = persistReducer(JAVASCRIPT_FIRST_NOT_NULL, conn);

                        Label joiningLabel = persistLabel(
                                newName,
                                testId,
                                reducerId,
                                io.hyperfoil.tools.horreum.api.exp.data.Label.MultiIterationType.Length.toString(),
                                io.hyperfoil.tools.horreum.api.exp.data.Label.ScalarVariableMethod.First.name(),
                                false,
                                null,
                                null,
                                targetGroupId,
                                null,
                                Collections.emptyList(),
                                conn,
                                testContext);
                        List<Extractor> extractors = persistedLabels.stream().map(persistedLabel -> persistExtractor(
                                persistedLabel.name,
                                null,
                                Type.VALUE,
                                null,
                                false,
                                joiningLabel.id,
                                persistedLabel.id,
                                conn)).toList();
                        joiningLabel.extractors.addAll(extractors);

                        testGroup.labels.add(joiningLabel);
                        if (targetGroupId != null && targetGroupId > -1) {
                            LabelGroup targetedGroup = loadGroup(targetGroupId, conn);
                            List<Label> loadedTargetGroupLabels = joiningLabel.targetGroup(targetedGroup, testContext, conn);
                            testGroup.labels.addAll(loadedTargetGroupLabels);
                        }
                    }
                });

                // this is the start of the original logic

                //                    ref.transforms.forEach(id -> {
                //                        LabelDef def = fromTransform(id, conn);
                //                        if (useCounter) {
                //                            def = new LabelDef(def.name + counter.getAndIncrement(), def.javascript, def.target,
                //                                    def.extractors);
                //                        }
                //
                //                        Long targetGroupId = null;
                //                        Long originalLabelId = null;
                //                        Long sourceGroupId = null;
                //                        if (transformParents.containsKey(id)) {
                //                            Label ancestorLabel = transformParents.get(id);
                //                            originalLabelId = ancestorLabel.id;
                //                            sourceGroupId = ancestorLabel.groupId;
                //
                //                        }
                //                        if (def.target != null && !def.target.isBlank()) {
                //                            targetGroupId = globalSchemas.getOrDefault(def.target, null);
                //                        }
                //
                //                        Label persistedLabel = persistLabelDef(def, testGroup.id, true, null, sourceGroupId, targetGroupId,
                //                                originalLabelId, conn, testContext);
                //                        testGroup.labels.add(persistedLabel);
                //                        addTempTransformMap(id, persistedLabel.id, conn);
                //                        //loading the targetGroup into testGroup, something is wrong with this
                //                        LabelGroup targetedGroup = loadGroup(targetGroupId, conn);
                //                        //This is a mistake, this shoudl be done by a label that merges all the transforms not individually
                //                        List<Label> loadedTargetGroupLabels = persistedLabel.targetGroup(targetedGroup, testContext, conn);
                //                        testGroup.labels.addAll(loadedTargetGroupLabels);
                //
                //                    });

                //we need to first create a transform
            } else {
                Map<String, List<String>> schemaReferences = getReferencedSchema(testId, conn);
                for (String jsonpath : schemaReferences.keySet()) {
                    Set<String> existingUri = schemaReferences.get(jsonpath).stream()
                            .filter(uri -> globalSchemas.get(uri) != null)
                            .collect(Collectors.toSet());
                    Set<Long> labelGroupsIds = existingUri.stream()
                            .map(globalSchemas::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());

                    assert existingUri.size() == labelGroupsIds.size();

                    System.out.println(jsonpath + " -> " + labelGroupsIds + " schemas =" + schemaReferences.get(jsonpath));
                    if (labelGroupsIds.isEmpty()) {
                        System.out.println(" unknown schema " + schemaReferences.get(jsonpath));
                    } else if ("$.\"$schema\"".equals(jsonpath)) {
                        //if this is something at the top of the json we just import the groups
                        if (labelGroupsIds.size() == 1) {
                            labelGroupsIds.stream()
                                    .map(id -> loadGroup(id, conn))
                                    .forEach(labelGroup -> {
                                        testGroup.addGroup(
                                                labelGroup,
                                                conn,
                                                testContext);
                                    });
                        } else {
                            List<Schema> schemas = existingUri.stream().map(uri -> fromSchemaUri(uri, conn)).toList();
                            List<LabelDef> mergedDefs = mergeLabels(schemas.stream().map(schema -> schema.labels).toList());
                            mergedDefs.stream()
                                    .map(labelDef -> persistLabelDef(labelDef, testGroup.id, false, null, null, null, null,
                                            conn, testContext))
                                    .forEach(testGroup.labels::add);
                        }

                    } else {
                        if (labelGroupsIds.size() == 1) {
                            Long targetGroupId = labelGroupsIds.iterator().next();
                            Label jsonpathLabel = persistLabel(
                                    jsonpath,
                                    testGroup.id,
                                    null /* reducerId */,
                                    io.hyperfoil.tools.horreum.api.exp.data.Label.MultiIterationType.Length.name(),
                                    io.hyperfoil.tools.horreum.api.exp.data.Label.ScalarVariableMethod.First.name(),
                                    false,
                                    null,
                                    null,
                                    targetGroupId,
                                    null,
                                    new ArrayList<>(List.of(
                                            new Extractor(null,
                                                    jsonpath.substring(0, jsonpath.length() - ".\"$schema\"".length()), null,
                                                    Type.PATH, null, null, jsonpath, false))),
                                    conn,
                                    testContext);
                            testContext.add(jsonpathLabel, null);
                            testGroup.labels.add(jsonpathLabel);
                            //loading the targetGroup into testGroup
                            LabelGroup targetedGroup = loadGroup(targetGroupId, conn);
                            List<Label> loadedTargetGroupLabels = jsonpathLabel.targetGroup(targetedGroup, testContext, conn);
                            testGroup.labels.addAll(loadedTargetGroupLabels);
                        } else {
                            System.out.println("!! multi-group merge under path " + labelGroupsIds);
                        }

                    }
                }
            }
            //now check

        }
        boolean ok = validateGroups(conn);
        System.out.println("ok = " + ok);
        persistTestRuns(conn);
        updateRunSeq(conn);
        //at this point we should be able all set
    }

    public static final String JAVASCRIPT_FIRST_NOT_NULL = "(args)=>Object.values(args).find(el => !!el)";

    // Used to resolve the correct labelId from either an old Id or Name
    private static class LabelContext {
        private final Map<String, Label> byName = new HashMap<>();
        private final Map<Long, String> byNewId = new HashMap<>();
        private final Map<Long, Label> byOldId = new HashMap<>();

        public boolean has(String name) {
            return byName.containsKey(name);
        }

        public boolean has(Long oldId) {
            return byOldId.containsKey(oldId);
        }

        public Label get(String name) {
            return byName.getOrDefault(name, null);
        }

        public String getNameFromNewId(Long newId) {
            return byNewId.getOrDefault(newId, null);
        }

        public Label get(Long oldId) {
            return byOldId.getOrDefault(oldId, null);
        }

        public Long getId(String name) {
            return has(name) ? get(name).id : null;
        }

        public Long getId(Long oldId) {
            return has(oldId) ? get(oldId).id : null;
        }

        public void add(Label l, Long oldId) {
            byName.put(l.name, l);
            byNewId.put(l.id, l.name);
            if (oldId != null && oldId > 0) {
                byOldId.put(oldId, l);
            }
        }
    }

    private record LabelGroup(Long id, String name, String owner, String type, List<Label> labels) {
        boolean isPersisted() {
            return id != null && id > 0;
        }

        public LabelGroup withOwner(String newOwner, Connection conn, LabelContext context) {
            LabelGroup rtrn = persistGroup(new LabelGroup(null, name, newOwner, type, new ArrayList<>()), conn, context);
            labels.forEach(label -> {
                rtrn.labels.add(label.withGroupSourceTarget(rtrn.id, context.getId(label.sourceLabelId),
                        label.targetGroupId, context, conn));
            });
            return rtrn;
        }

        public void addGroup(LabelGroup group, Connection conn, LabelContext context) {
            //TODO should this check for unique name conflicts?
            group.labels.forEach(label -> {
                Label newLabel = label.withGroupSourceTarget(
                        id,
                        context.getId(label.sourceLabelId),
                        label.targetGroupId,
                        context,
                        conn);
                labels.add(newLabel);
            });
        }
    }

    private static List<LabelDef> mergeLabels(List<List<LabelDef>> groups) {
        List<LabelDef> rtrn = new ArrayList<>();
        Map<String, List<LabelDef>> labelsByName = groups.stream().flatMap(e -> e.stream()).collect(
                Collectors.toMap(
                        l -> l.name,
                        l -> new ArrayList(Collections.singletonList(l)),
                        (a, b) -> {
                            a.addAll(b);
                            return a;
                        }));
        for (String labelName : labelsByName.keySet()) {
            List<LabelDef> conflictingLabels = labelsByName.get(labelName);
            if (conflictingLabels.size() == 1) {
                rtrn.add(conflictingLabels.get(0));
            } else {
                AtomicInteger counter = new AtomicInteger(1);
                List<LabelDef> newLabels = conflictingLabels.stream().map(l -> new LabelDef(
                        l.name + counter.getAndIncrement(),
                        l.javascript,
                        null, /* l.target omitted because not supported */
                        l.extractors)).toList();
                List<ExtractorDef> newExtractors = newLabels.stream().map(l -> {
                    return new ExtractorDef(l.name, l.name);
                }).toList();
                rtrn.addAll(newLabels);
                rtrn.add(new LabelDef(
                        labelName,
                        JAVASCRIPT_FIRST_NOT_NULL,
                        null,
                        newExtractors));
            }
        }
        return rtrn;
    }

    private static LabelGroup createMergedGroup(String newName, String owner, String type, List<LabelGroup> groups,
            Connection conn, LabelContext context) {

        long beforeJavaLabelCount = groups.stream().mapToLong(g -> g.labels.size()).sum();
        long beforeDbLabelCount = groups.stream().mapToLong(g -> countGroupLabels(g.id, conn)).sum();
        List<Long> beforeCounts = groups.stream().map(g -> countGroupLabels(g.id, conn)).toList();

        LabelGroup newGroup = persistGroup(new LabelGroup(null, newName, owner, type, new ArrayList<>()), conn, context);
        Map<String, List<Label>> labelsByName = new HashMap<>();
        groups.stream().flatMap(group -> group.labels.stream()).forEach(label -> {
            labelsByName.putIfAbsent(label.name, new ArrayList<>());
            labelsByName.get(label.name).add(label);
        });
        for (String name : labelsByName.keySet()) {
            List<Label> labelList = labelsByName.get(name);
            if (labelList.size() == 1 || allTheSameLabels(labelList)) {
                Label first = labelList.get(0);
                Label newLabel = first.withGroupSourceTarget(
                        newGroup.id,
                        context.getId(first.sourceLabelId),
                        first.targetGroupId,
                        context,
                        conn);
                newGroup.labels.add(newLabel);
            } else {
                Set<String> newNames = new HashSet<>();
                AtomicInteger counter = new AtomicInteger();
                for (Label l : labelList) {
                    String newLabelName = l.name + counter.getAndIncrement();
                    while (newNames.contains(newLabelName)) {
                        newLabelName += counter.getAndIncrement();
                    }
                    newNames.add(newLabelName);
                    Label newLabel = persistLabel(
                            newLabelName,
                            newGroup.id,
                            l.reducerId,
                            io.hyperfoil.tools.horreum.api.exp.data.Label.MultiIterationType.Length.name(),
                            io.hyperfoil.tools.horreum.api.exp.data.Label.ScalarVariableMethod.First.name(),
                            l.splitting,
                            l.sourceLabelId,
                            l.sourceGroupId,
                            l.targetGroupId,
                            l.originalLabelId,
                            new ArrayList<>(),
                            conn,
                            context);
                    context.add(newLabel, l.id);
                    l.extractors.forEach(e -> {
                        newLabel.extractors.add(
                                persistExtractor(
                                        e.name,
                                        e.jsonpath,
                                        e.type,
                                        e.columnName,
                                        e.foreach,
                                        newLabel.id,
                                        context.getId(e.targetLabelId),
                                        conn));
                    });
                }
                List<Extractor> extractors = newNames.stream().map(newLabelName -> {
                    return new Extractor(null, newLabelName, null, Type.VALUE, null, null, newLabelName + NAME_SEPARATOR,
                            false);
                }).toList();
                //                    Label joiningLabel = new LabelDef(labelName, "(args)=>Object.values(args).find(el => !!el)", null,
                //                            extractors);
                Long reducerId = persistReducer("(args)=>Object.values(args).find(el => !!el)", conn);
                Label joiningLabel = persistLabel(
                        name,
                        newGroup.id,
                        reducerId,
                        io.hyperfoil.tools.horreum.api.exp.data.Label.MultiIterationType.Length.name(),
                        io.hyperfoil.tools.horreum.api.exp.data.Label.ScalarVariableMethod.First.name(),
                        false,
                        null /* sourceLabelId */,
                        null /* sourceGroupId */,
                        null /* targetGroupId */,
                        null /* originalLabelId */,
                        extractors,
                        conn,
                        context);
                newGroup.labels.add(joiningLabel);
            }
        }

        long afterJavaLabelCount = groups.stream().mapToLong(g -> g.labels.size()).sum();
        long afterDbLabelCount = groups.stream().mapToLong(g -> countGroupLabels(g.id, conn)).sum();
        long afterNewGroupCount = countGroupLabels(newGroup.id, conn);
        List<Long> afterCounts = groups.stream().map(g -> countGroupLabels(g.id, conn)).toList();

        assert (beforeJavaLabelCount == afterJavaLabelCount);
        assert (beforeDbLabelCount == afterDbLabelCount);
        assert (afterNewGroupCount == beforeDbLabelCount);
        assert (beforeCounts.size() == afterCounts.size());
        for (int idx = 0; idx < beforeCounts.size(); idx++) {
            assert (beforeCounts.get(idx).equals(afterCounts.get(idx)));
        }

        return newGroup;
    }

    private record ExtractorDef(String name, String path) {
        public Extractor toExtractor(LabelContext context) {
            ExtractorDao.Type type = Type.PATH;
            String columnName = null;
            boolean forEach = false;
            Long targetId = null;
            String jsonpath = path;

            if (jsonpath.startsWith(PREFIX) || jsonpath.startsWith(FOR_EACH_SUFFIX + NAME_SEPARATOR)) {
                if (jsonpath.startsWith(FOR_EACH_SUFFIX + NAME_SEPARATOR)) {
                    forEach = true;
                    jsonpath = jsonpath.substring(FOR_EACH_SUFFIX.length() + NAME_SEPARATOR.length());
                }
            } else if (jsonpath.startsWith(METADATA_PREFIX)) {
                //TODO support metadata column access
            } else {
                type = ExtractorDao.Type.VALUE;
                String labelName = jsonpath;
                if (jsonpath.contains(NAME_SEPARATOR)) {
                    labelName = jsonpath.substring(0, jsonpath.indexOf(NAME_SEPARATOR));
                    jsonpath = jsonpath.substring(jsonpath.indexOf(NAME_SEPARATOR) + NAME_SEPARATOR.length());
                }
                if (labelName.endsWith(FOR_EACH_SUFFIX)) {
                    forEach = true;
                    labelName = labelName.substring(0, labelName.length() - FOR_EACH_SUFFIX.length());
                }
                Label found = context.get(labelName);
                System.out.println("fetched " + labelName + " as " + found);
                if (found != null) {
                    targetId = found.id;
                }

            }
            return new Extractor(
                    null,
                    name,
                    null,
                    type,
                    targetId,
                    columnName,
                    jsonpath,
                    forEach);

        }
    }

    private record Extractor(Long id, String name, Long parentLabelId, ExtractorDao.Type type, Long targetLabelId,
            String columnName, String jsonpath, boolean foreach) {

        boolean isPersisted() {
            return id != null && id > 0;
        }

        boolean hasTarget() {
            return targetLabelId != null && targetLabelId > 0;
        }

        @Override
        public boolean equals(Object obj) {
            boolean rtrn = false;
            if (obj instanceof Extractor e) {
                rtrn = e.name.equals(name) && e.jsonpath.equals(jsonpath) && e.foreach == foreach;
            }
            return rtrn;
        }

        public Extractor withParentAndTarget(long newParentId, Long newTargetId, Connection conn, LabelContext context) {
            //the targetId will get detected by persistExtractor
            return persistExtractor(new Extractor(null, name, newParentId, type, newTargetId, columnName, jsonpath, foreach),
                    conn, context);
        }

        public ExtractorDef toExtractorDef(LabelContext context) {
            String namePrefix = type.equals(Type.VALUE) ? context.getNameFromNewId(targetLabelId)
                    : type.equals(Type.METADATA) ? METADATA_PREFIX + columnName + METADATA_SUFFIX : "";
            String newJsonpath = namePrefix + (foreach ? FOR_EACH_SUFFIX : "")
                    + (!namePrefix.isBlank() && jsonpath != null && !jsonpath.isBlank() ? NAME_SEPARATOR : "") + jsonpath;
            return new ExtractorDef(name, newJsonpath);
        }
    }

    private record Label(Long id, String name, Long reducerId, Long targetGroupId, Long groupId, boolean splitting,
            Long sourceLabelId, Long sourceGroupId, Long originalLabelId, List<Extractor> extractors) {
        boolean isPersisted() {
            return id != null && id > 0;
        }

        //adds the newly created label to the context
        //io.hyperfoil.tools.horreum.exp.data.LabelDao.loadGroup
        public Label withGroupSourceTarget(long newGroupId, Long newSourceLabelId, Long newTargetId, LabelContext context,
                Connection conn) {

            long beforeExtractors = extractors.size();

            Label rtrn = persistLabel(
                    name,
                    newGroupId,
                    reducerId,
                    io.hyperfoil.tools.horreum.api.exp.data.Label.MultiIterationType.Length.name(),
                    io.hyperfoil.tools.horreum.api.exp.data.Label.ScalarVariableMethod.First.name(),
                    splitting,
                    newSourceLabelId,
                    groupId,
                    newTargetId,
                    id,
                    new ArrayList<>(),
                    conn,
                    context);
            context.add(rtrn, id);
            //same Extractor transformation that occurs in the LabelDao.loadGroup
            extractors.forEach(e -> {
                Long newTargetLabelId = e.targetLabelId;
                ExtractorDao.Type type = e.type;
                if (newSourceLabelId != null && newSourceLabelId > 0 && e.type == Type.PATH) {//only chamge extractors that reference a path
                    type = Type.VALUE;
                    newTargetLabelId = newSourceLabelId;
                }
                rtrn.extractors.add(
                        persistExtractor(
                                e.name,
                                e.jsonpath,
                                type,
                                e.columnName,
                                e.foreach,
                                rtrn.id,
                                newTargetLabelId,
                                conn));
            });

            long afterExtractors = countLabelExtractors(rtrn.id, conn);
            assert (beforeExtractors == afterExtractors);

            return rtrn;
        }

        //TODO we also need a GroupContext for copies of schema that target schema? maybe not as that isn't supported by the old model
        public List<Label> targetGroup(LabelGroup group, LabelContext context, Connection conn) {
            long beforeParentGroupCount = countGroupLabels(groupId, conn);
            long beforeTargetGroupCount = countGroupLabels(group.id, conn);
            System.out.println("label.targetGroup before extractor count = " + countTotalExtractors(conn) + " adding "
                    + group.labels.stream().mapToInt(l -> l.extractors.size()).sum() + " from " + group.name + " [" + group.id
                    + "] to " + name);
            List<Label> rtrn = new ArrayList<>();

            group.labels.forEach(label -> {
                Label newLabel = label.withGroupSourceTarget(groupId, id, label.targetGroupId, context, conn);
                //withGroupSourceTarget adds the newLabel to the context
                rtrn.add(newLabel);
            });

            long afterParentGroupCount = countGroupLabels(groupId, conn);
            long afterTargetGroupCount = countGroupLabels(group.id, conn);

            assert (beforeTargetGroupCount == afterTargetGroupCount);
            assert (afterParentGroupCount == beforeParentGroupCount + beforeTargetGroupCount);

            System.out.println("label.targetGroup after extractor count = " + countTotalExtractors(conn));
            return rtrn;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Label l) {
                return Objects.equals(name, l.name) &&
                        Objects.equals(reducerId, l.reducerId) &&
                        Objects.equals(targetGroupId, l.targetGroupId) &&
                        splitting == l.splitting &&
                        extractors.stream().allMatch(e -> l.extractors.stream().anyMatch(le -> le.equals(e)));
            }
            return false;
        }

        @Override
        public int hashCode() {
            List<Object> toHash = new ArrayList<>(extractors);
            toHash.addAll(Arrays.asList(name, reducerId, targetGroupId, splitting));
            return Objects.hash(toHash);
        }
    }

    private record LabelDef(String name, String javascript, String target, List<ExtractorDef> extractors) {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof LabelDef l) {
                if (l.name.equals(name) && (Objects.equals(l.javascript, javascript) && Objects.equals(l.target, target))
                        && l.extractors.size() == extractors.size()) {
                    return extractors.stream().allMatch(e -> l.extractors.stream().anyMatch(le -> le.equals(e)));
                } else {
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            List<Object> toHash = new ArrayList<>(extractors);
            toHash.addAll(Arrays.asList(name, javascript, target));
            return Objects.hash(toHash);
        }
    }

    private static Schema fromSchemaUri(String uri, Connection conn) {
        Schema rtrn = null;
        try (PreparedStatement schemaPs = conn.prepareStatement("select id,name,uri,owner from schema where uri = ?");
                PreparedStatement labelPs = conn.prepareStatement("select id,name,function from label where schema_id = ?");
                PreparedStatement extractorPs = conn
                        .prepareStatement("select name, jsonpath, isarray from label_extractors where label_id = ?")) {
            schemaPs.setString(1, uri);
            try (ResultSet rs = schemaPs.executeQuery()) {
                //should only find one
                if (rs.next()) {
                    Long id = rs.getLong("id");
                    String name = rs.getString("name");
                    String owner = rs.getString("owner");
                    List<LabelDef> labels = new ArrayList<>();
                    rtrn = new Schema(id, name, uri, owner, labels);
                }
            }
            labelPs.setLong(1, rtrn.id);
            try (ResultSet labelRs = labelPs.executeQuery()) {
                while (labelRs.next()) {
                    Long id = labelRs.getLong("id");
                    String name = labelRs.getString("name");
                    String function = labelRs.getString("function");
                    List<ExtractorDef> extractorList = new ArrayList<>();
                    LabelDef label = new LabelDef(name, function, "", extractorList);
                    rtrn.labels.add(label);
                    extractorPs.setLong(1, id);
                    try (ResultSet extractorRs = extractorPs.executeQuery()) {
                        while (extractorRs.next()) {
                            String extractorName = extractorRs.getString("name");
                            String jsonpath = extractorRs.getString("jsonpath");
                            boolean isArray = extractorRs.getBoolean("isarray");
                            //TODO is passing in null the expected behavior?
                            //TODO is the extractor isArray important?
                            //Extractor e = new Extractor(null, extractorName, null, Type.PATH, null, null, jsonpath, isArray);
                            extractorList.add(new ExtractorDef(extractorName, jsonpath));
                        }
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    record Schema(long id, String name, String uri, String owner, List<LabelDef> labels) {
    }

    record TestTransformsRef(long testId, List<Long> transforms) {
    }

    /**
     * gets a list of TestTransformsRef that is the reference from testId to each transformId
     * @param conn
     * @return
     */
    private static List<TestTransformsRef> getTestTransforms(Connection conn) {
        List<TestTransformsRef> rtrn = new ArrayList<>();
        try (PreparedStatement statement = conn.prepareStatement(
                "select test_id,jsonb_agg(transformer_id) as transformer_ids from test_transformers group by test_id");
                ResultSet resultSet = statement.executeQuery();) {
            if (resultSet != null) {
                System.out.println("warnings = " + resultSet.getWarnings());
                while (resultSet.next()) {
                    Long testId = resultSet.getLong("test_id");
                    ArrayNode nodes = (ArrayNode) new ObjectMapper().readTree(resultSet.getString("transformer_ids"));
                    ArrayList<Long> transformIds = new ArrayList<>();
                    for (JsonNode node : nodes) {
                        transformIds.add(node.longValue());
                    }
                    rtrn.add(new TestTransformsRef(testId, transformIds));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    private static void addTempTransformMap(long transformId, long labelId, Connection conn) {
        try (PreparedStatement ps = conn
                .prepareStatement("insert into exp_temp_map_transform (transformId, expLabelId) values (?,?)")) {
            ps.setLong(1, transformId);
            ps.setLong(2, labelId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void addTempSchemaMap(long schemaId, long labelGroupId, Connection conn) {
        try (PreparedStatement ps = conn
                .prepareStatement("insert into exp_temp_map_schema (schemaId,expLabelGroupId) values (?,?)")) {
            ps.setLong(1, schemaId);
            ps.setLong(2, labelGroupId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * returns a map of jsonpath to the list of schema uris found at that jsonpath
     * @param testId
     * @param conn
     * @return
     */
    private static Map<String, List<String>> getReferencedSchema(long testId, Connection conn) {
        Map<String, List<String>> rtrn = new HashMap<>();
        try (
                PreparedStatement ps = conn.prepareStatement(
                        """
                                with paths as (select jsonb_paths(data,'$',2) as path, id from run where testid = ?), schemapaths as ( select distinct path,jsonb_path_query_first(r.data,path::jsonpath) as value from paths left join run r on r.id = paths.id where path like '%."$schema"') select path, jsonb_agg(value) as schemas from schemapaths group by path;
                                """)) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String path = rs.getString("path");
                    ArrayNode nodes = (ArrayNode) new ObjectMapper().readTree(rs.getString("schemas"));
                    List<String> schemas = new ArrayList<>();
                    nodes.forEach(node -> schemas.add(node.textValue()));
                    rtrn.put(path, schemas);
                }
            } catch (JsonMappingException e) {
                throw new RuntimeException(e);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    private static void addTempTestMap(long testId, long labelGroupId, Connection conn) {
        try (PreparedStatement ps = conn
                .prepareStatement("insert into exp_temp_map_tests (testid,explabelGroupId) values (?,?)")) {
            ps.setLong(1, testId);
            ps.setLong(2, labelGroupId);
            ps.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> getUniqueLabelNames(List<Schema> schemas) {
        return schemas.stream().flatMap(s -> s.labels().stream()).map(l -> l.name).collect(Collectors.toSet());
    }

    private static Map<String, Long> createGlobalSchemas(Connection conn) {
        Map<String, Long> uriToGroupId = new HashMap<>();
        Map<String, List<Schema>> groupedSchemas = new HashMap<>();

        Pattern p = Pattern.compile("(?<base>.*?(?=[:v][\\d.]+)?)(?<version>[:v][\\d.]+)?");
        Matcher m = p.matcher("");

        try (PreparedStatement ps = conn.prepareStatement("select id,uri from schema order by uri");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String uri = rs.getString("uri");
                m.reset(uri);
                String baseUri = m.matches() ? m.group("base") : uri;
                Schema s = fromSchemaUri(uri, conn);
                groupedSchemas.putIfAbsent(baseUri, new ArrayList<>());
                groupedSchemas.get(baseUri).add(s);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        System.out.println("unique Uri " + groupedSchemas.size());
        System.out.println("total schemas " + groupedSchemas.values().stream().mapToInt(List::size).sum());

        //now we check each collection of schemas
        for (String baseUri : groupedSchemas.keySet()) {
            LabelContext context = new LabelContext();
            System.out.println(baseUri + " " + groupedSchemas.get(baseUri).size() + " bizbuz "
                    + groupedSchemas.get(baseUri).stream().map(schema -> schema.uri).collect(Collectors.toSet()));
            List<Schema> schemas = groupedSchemas.get(baseUri);
            Set<String> uniqueLabelNames = getUniqueLabelNames(schemas);
            List<LabelDef> newLabels = new ArrayList<>();
            for (String labelName : uniqueLabelNames) {
                //this loses the uri of the owning schema, we need that if we are going to create a merge
                List<LabelDef> labels = schemas.stream()
                        .map(s -> s.labels.stream().filter(l -> labelName.equals(l.name)).findFirst().orElse(null))
                        .filter(Objects::nonNull).toList();
                if (labels.size() == 1 || allTheSameLabelDefs(labels)) {

                    newLabels.add(labels.get(0));
                } else {//we need to create a merge
                    System.out.println("  creating joining Label for " + baseUri + " " + labelName);
                    AtomicInteger counter = new AtomicInteger(1);
                    Set<String> newNames = new HashSet<>();
                    Set<LabelDef> uniqueLabels = schemas.stream()
                            .map(s -> s.labels.stream().filter(l -> labelName.equals(l.name)).findFirst().orElse(null))
                            .filter(Objects::nonNull).collect(Collectors.toSet());

                    System.out.println("    labels.size=" + labels.size() + "  uniqueLabels.size=" + uniqueLabels.size());
                    System.out.println("    All labels:");
                    labels.forEach(l -> System.out.println("      " + l));
                    System.out.println("    Unique labels:");
                    uniqueLabels.forEach(l -> System.out.println("      " + l));

                    schemas.stream().forEach(s -> {
                        LabelDef found = s.labels.stream().filter(l -> labelName.equals(l.name)).findFirst().orElse(null);
                        m.reset(s.uri);
                        if (found != null && uniqueLabels.contains(found)) {

                            uniqueLabels.remove(found);
                            String newName = found.name + (m.matches() ? m.group("version") : "" + counter.getAndIncrement());
                            newName = newName.replaceAll("[:.,;]", "");
                            while (newNames.contains(newName)) {
                                newName = newName + counter.getAndIncrement();
                            }
                            System.out.println("  adding   " + found + "\n  as " + newName + " from " + s.uri);
                            newNames.add(newName);
                            LabelDef newLabel = new LabelDef(newName, found.javascript, found.target, found.extractors);

                            newLabels.add(newLabel);
                        } else if (found != null) {
                            System.out.println("  skipping " + found);
                        }
                    });
                    //create the new label
                    List<ExtractorDef> extractors = newNames.stream().map(name -> new ExtractorDef(name, name)).toList();
                    LabelDef joiningLabel = new LabelDef(labelName, JAVASCRIPT_FIRST_NOT_NULL, null,
                            extractors);
                    newLabels.add(joiningLabel);
                }
            }
            //now it's time to persist the new Schema
            Schema newSchema = new Schema(-1, baseUri, baseUri, "GLOBAL", newLabels);
            LabelGroup labelGroup = persistSchema(newSchema, conn, context);
            schemas.forEach(s -> {
                uriToGroupId.put(s.uri, labelGroup.id);
                addTempSchemaMap(s.id, labelGroup.id, conn);
            });

        }
        return uriToGroupId;
    }

    private static Long persistReducer(String javascript, Connection conn) {
        Long reducerId = null;
        if (javascript != null && !javascript.isBlank()) {
            try (PreparedStatement insertReducer = conn.prepareStatement(
                    "insert into exp_label_reducers (id,function) values (nextval('exp_label_reducers_seq'),?) RETURNING id");) {
                insertReducer.setString(1, javascript);
                try (ResultSet rs = insertReducer.executeQuery()) {
                    if (rs.next()) {
                        reducerId = rs.getLong(1);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return reducerId;
    }

    private static Label persistLabelDef(LabelDef l, long groupId, boolean splitting, Long sourceLabelId,
            Long sourceGroupId,
            Long targetGroupId,
            Long originalLabelId, Connection conn, LabelContext context) {
        Long reducerId = persistReducer(l.javascript, conn);
        return persistLabel(l.name,
                groupId,
                reducerId,
                io.hyperfoil.tools.horreum.api.exp.data.Label.MultiIterationType.Length.name(),
                io.hyperfoil.tools.horreum.api.exp.data.Label.ScalarVariableMethod.First.name(),
                splitting,
                sourceLabelId,
                sourceGroupId,
                targetGroupId,
                originalLabelId,
                l.extractors.stream().map(e -> e.toExtractor(context)).toList(),
                conn,
                context);

    }

    //all the Ids better be in order
    private static Extractor persistExtractor(Extractor e, Connection conn, LabelContext context) {
        return persistExtractor(e.name, e.jsonpath, e.type, e.columnName, e.foreach, e.parentLabelId, e.targetLabelId, conn);
    }

    private static Extractor persistExtractorDef(ExtractorDef def, Long parentId, Long targetId,
            LabelContext context, Connection conn) {
        ExtractorDao.Type type = Type.PATH;
        String columnName = null;
        boolean forEach = false;

        String name = def.name;
        String jsonpath = def.path;

        if (jsonpath.startsWith(PREFIX) || jsonpath.startsWith(FOR_EACH_SUFFIX + NAME_SEPARATOR)) {
            if (jsonpath.startsWith(FOR_EACH_SUFFIX + NAME_SEPARATOR)) {
                forEach = true;
                jsonpath = jsonpath.substring(FOR_EACH_SUFFIX.length() + NAME_SEPARATOR.length());
            }
        } else if (jsonpath.startsWith(METADATA_PREFIX)) {
            //TODO support metadata column access
        } else {
            type = ExtractorDao.Type.VALUE;
            String labelName = jsonpath;
            if (jsonpath.contains(NAME_SEPARATOR)) {
                labelName = jsonpath.substring(0, jsonpath.indexOf(NAME_SEPARATOR));
                jsonpath = jsonpath.substring(jsonpath.indexOf(NAME_SEPARATOR) + NAME_SEPARATOR.length());
            }
            if (labelName.endsWith(FOR_EACH_SUFFIX)) {
                forEach = true;
                labelName = labelName.substring(0, labelName.length() - FOR_EACH_SUFFIX.length());
            }
            Label found = context.get(labelName);
            System.out.println("fetched " + labelName + " as " + found);
            if (found != null) {
                targetId = found.id;
            }

        }
        return persistExtractor(
                name,
                jsonpath,
                type,
                columnName,
                forEach,
                parentId,
                targetId,
                conn);
    }

    private static Extractor persistExtractor(String name, String jsonpath, ExtractorDao.Type type, String columnName,
            boolean forEach, Long parentId, Long targetId, Connection conn) {
        try (PreparedStatement insertExtractor = conn.prepareStatement(
                "insert into exp_extractor (id,name,jsonpath,type,column_name,foreach,parent_id,target_id) values (nextval('exp_extractor_seq'),?,?,?,?,?,?,?) RETURNING id")) {
            insertExtractor.setString(1, name);
            insertExtractor.setString(2, jsonpath);
            insertExtractor.setString(3, type.name());
            insertExtractor.setString(4, columnName);//column_name only exists for metadata extractors
            insertExtractor.setBoolean(5, forEach);//forEach didn't exist in old system
            insertExtractor.setObject(6, parentId);
            insertExtractor.setObject(7, targetId);//extracting from label's values doesn't exist in original model
            try (ResultSet rs = insertExtractor.executeQuery()) {
                if (rs.next()) {
                    return new Extractor(rs.getLong(1), name, parentId, type, targetId, columnName, jsonpath, forEach);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private static Label persistLabel(String name, long groupId, Long reducerId, String multitype, String scalarmethod,
            boolean splitting, Long sourceLabelId,
            Long sourceGroupId, Long targetGroupId, Long originalLabelId, List<Extractor> extractors, Connection conn,
            LabelContext context) {

        Long newId = null;
        try (PreparedStatement insertLabel = conn.prepareStatement(
                """
                            insert into exp_label (
                                id,
                                name,
                                reducer_id,
                                multitype,
                                scalarmethod,
                                splitting,
                                dirty,
                                group_id,
                                sourcelabel_id,
                                sourcegroup_id,
                                targetgroup_id,
                                originallabel_id
                            ) values (nextval('exp_label_seq'),?,?,?,?,?,?,?,?,?,?,?) RETURNING id
                        """)) {
            insertLabel.setString(1, name);
            insertLabel.setObject(2, reducerId);
            insertLabel.setString(3, multitype);
            insertLabel.setString(4, scalarmethod);
            insertLabel.setBoolean(5, splitting);
            insertLabel.setBoolean(6, false);//dirty is not supported by previous model
            insertLabel.setLong(7, groupId);
            insertLabel.setObject(8, sourceLabelId);
            insertLabel.setObject(9, sourceGroupId);
            insertLabel.setObject(10, targetGroupId);
            insertLabel.setObject(11, originalLabelId);
            try (ResultSet rs = insertLabel.executeQuery()) {
                if (rs.next()) {
                    newId = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Label rtrn = new Label(newId, name, reducerId, targetGroupId, groupId, splitting, sourceLabelId, sourceGroupId,
                originalLabelId, new ArrayList<>());

        extractors.forEach(e -> {
            rtrn.extractors.add(
                    e.withParentAndTarget(rtrn.id, context.getId(e.targetLabelId), conn, context));
        });

        return rtrn;
    }

    private static LabelGroup persistSchema(Schema schema, Connection conn, LabelContext context) {
        LabelGroup group = persistGroup(new LabelGroup(null, schema.uri, schema.owner, "group", new ArrayList<>()), conn,
                context);
        //necessary because the persisted LabelGroup did not have labels
        schema.labels.forEach(l -> {
            Label label = persistLabelDef(l, group.id, false, null, null, null, null, conn, context);
            group.labels.add(label);
        });
        return group;
    }

    /**
     * Copy over all old tests to label_groups so that we preserve their ids
     * @param conn
     */
    private static void persistAllTests(Connection conn) {
        try (PreparedStatement ps = conn
                .prepareStatement("insert into exp_labelgroup (id,type,name,owner) select id, 'test', name, owner from test")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void updateLabelGroupSeq(Connection conn) {
        try (PreparedStatement ps = conn
                .prepareStatement("select setval('exp_labelgroup_seq', (select max(id)+1 from exp_labelgroup), false)");
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {

            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void persistTestRuns(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "insert into exp_run (id,test_id,data,metadata) select r.id,r.testid,r.data,r.metadata from run r where exists (select 1 from exp_labelgroup g where g.id = r.testid)")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static void updateRunSeq(Connection conn) {
        try (PreparedStatement ps = conn
                .prepareStatement("select setval('exp_run_seq', (select max(id)+1 from exp_run), false)");
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {

            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static LabelGroup persistGroup(LabelGroup group, Connection conn, LabelContext context) {
        long newId = -1;
        assert !group.isPersisted();
        //        if(group.isPersisted()){
        //            try (PreparedStatement ps = conn.prepareStatement("insert into exp_labelgroup (id,type,name,owner) values (?,?,?,?) returning id")){
        //                ps.setLong(1,group.id);
        //                ps.setString(2,group.type);
        //                ps.setString(3,group.name);
        //                ps.setString(4,group.owner);
        //            } catch (SQLException e) {
        //                throw new RuntimeException(e);
        //            }
        //        }else {
        try (PreparedStatement ps = conn.prepareStatement(
                "insert into exp_labelgroup (id,type,name,owner) values (nextval('exp_labelgroup_seq'),?,?,?) returning id")) {

            ps.setString(1, group.type);
            ps.setString(2, group.name);
            ps.setString(3, group.owner);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    newId = rs.getLong(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        //        }
        LabelGroup rtrn = new LabelGroup(newId, group.name, group.owner, group.type, new ArrayList<>());
        group.labels.forEach(label -> {
            rtrn.labels.add(
                    label.withGroupSourceTarget(
                            rtrn.id,
                            context.getId(label.sourceLabelId),
                            context.getId(label.targetGroupId),
                            context, conn));
        });
        return rtrn;
    }

    private static LabelGroup loadGroup(long groupId, Connection conn) {
        LabelGroup rtrn = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "select id,name,type,owner from exp_labelgroup where id=?")) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rtrn = new LabelGroup(rs.getLong("id"), rs.getString("name"), rs.getString("owner"), rs.getString("type"),
                            new ArrayList<>());
                } else {
                    //TODO alert to failure?
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (rtrn != null) {
            rtrn.labels.addAll(loadLabels(groupId, conn));
        }
        return rtrn;
    }

    private static List<Label> loadLabels(long groupId, Connection conn) {
        List<Label> rtrn = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "select id,group_id,name,reducer_id,splitting,multitype,scalarmethod,sourcelabel_id,sourcegroup_id,targetgroup_id,originallabel_id from exp_label where group_id = ?")) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Label nextLabel = new Label(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getObject("reducer_id", Long.class),
                            rs.getObject("targetgroup_id", Long.class),
                            rs.getLong("group_id"),
                            rs.getBoolean("splitting"),
                            rs.getObject("sourcelabel_id", Long.class),
                            rs.getObject("sourcegroup_id", Long.class),
                            rs.getObject("originallabel_id", Long.class),
                            new ArrayList<>());
                    rtrn.add(nextLabel);
                    try (PreparedStatement exPs = conn.prepareStatement(
                            "select id,name,jsonpath,type,column_name,parent_id,target_id,foreach from exp_extractor where parent_id = ?")) {
                        exPs.setLong(1, nextLabel.id);
                        try (ResultSet exRs = exPs.executeQuery()) {
                            while (exRs.next()) {
                                nextLabel.extractors.add(
                                        new Extractor(
                                                exRs.getLong("id"),
                                                exRs.getString("name"),
                                                exRs.getLong("parent_id"),
                                                Type.valueOf(exRs.getString("type")),
                                                exRs.getObject("target_id", Long.class),
                                                exRs.getString("column_name"),
                                                exRs.getString("jsonpath"),
                                                exRs.getBoolean("foreach")));
                            }
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    private Label loadLabel(long id, Connection conn) {
        Label rtrn = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "select id,group_id,name,reducer_id,multitype,scalarmethod,sourcelabel_id,sourcegroup_id,targetgroup_id,originallabel_id from exp_label where id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    rtrn = new Label(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getObject("reducer_id", Long.class),
                            rs.getObject("targetgroup_id", Long.class),
                            rs.getLong("group_id"),
                            rs.getBoolean("splitting"),
                            rs.getObject("sourcelabel_id", Long.class),
                            rs.getObject("sourcegroup_id", Long.class),
                            rs.getObject("originallabel_id", Long.class),
                            new ArrayList<>());
                } else {
                    //TODO raise message?
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (rtrn != null) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "select id,name,jsonpath,type,column_name,parent_id,target_id,foreach from exp_extractor where parent_id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rtrn.extractors.add(
                                new Extractor(
                                        rs.getLong("id"),
                                        rs.getString("name"),
                                        rs.getLong("parent_id"),
                                        Type.valueOf(rs.getString("type")),
                                        rs.getObject("target_id", Long.class),
                                        rs.getString("column_name"),
                                        rs.getString("jsonpath"),
                                        rs.getBoolean("foreach")));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return rtrn;
    }

    private static LabelGroup fromTest(long testId, Connection conn) {
        LabelGroup rtrn = null;
        try (PreparedStatement ps = conn.prepareStatement(
                "select name,owner from test where id = ?")) {
            ps.setLong(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    String owner = rs.getString("owner");
                    rtrn = new LabelGroup(null, name, owner, "test", new ArrayList<>());
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    /**
     * This is the set of transformerIds that are shared across owners and therefore should have a "parent group"
     * @param conn
     * @return
     */
    private static Set<Long> getSharedTransformIds(Connection conn) {
        Set<Long> rtrn = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "with bag as (select l.name,x.transformer_id, array_agg(distinct t.owner) as owners from test_transformers x left join test t on t.id = x.test_id left join transformer l on l.id = x.transformer_id group by x.transformer_id,l.name) select transformer_id from bag where cardinality(owners) > 1");
                ResultSet rs = ps.executeQuery();) {
            while (rs.next()) {
                Long id = rs.getLong("transformer_id");
                rtrn.add(id);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    private static Set<Long> getAllTestIds(Connection conn) {
        Set<Long> rtrn = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("select id from test"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rtrn.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    private static LabelDef fromTransform(long transformId, Connection conn) {
        LabelDef rtrn = null;
        try (PreparedStatement extractorPs = conn.prepareStatement(
                "select name,jsonpath,isarray from transformer_extractors where transformer_id = ?");
                PreparedStatement transformPs = conn.prepareStatement(
                        "select name,function,targetschemauri from transformer where id = ?")) {
            List<ExtractorDef> extractors = new ArrayList<>();
            extractorPs.setLong(1, transformId);
            transformPs.setLong(1, transformId);
            try (ResultSet rs = extractorPs.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String jsonpath = rs.getString("jsonpath");
                    boolean foreach = rs.getBoolean("isarray");
                    //I don't think the old isarray should map to the new foreach with the extractors
                    ExtractorDef e = new ExtractorDef(name, jsonpath);
                    extractors.add(e);
                }
            }
            try (ResultSet rs = transformPs.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    String function = rs.getString("function");
                    String targetschemauri = rs.getString("targetschemauri");
                    rtrn = new LabelDef(name, function, targetschemauri, extractors);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    private static boolean allTheSameLabelDefs(List<LabelDef> labels) {
        if (labels.size() <= 1) {
            return true;
        } else {
            return labels.stream().allMatch(l -> l.equals(labels.get(0)));
        }
    }

    private static boolean allTheSameLabels(List<Label> labels) {
        if (labels.size() <= 1) {
            return true;
        } else {
            return labels.stream().allMatch(l -> l.equals(labels.get(0)));
        }
    }

    /**
     * Checks that all labels from target groups were copied into this group (if the group is a test group)
     * @param conn
     * @return
     */
    private static boolean validateGroups(Connection conn) {
        boolean rtrn = true;
        List<Long> groupIds = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("select id from exp_labelgroup order by id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                groupIds.add(rs.getLong("id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        for (long groupId : groupIds) {
            LabelGroup group = loadGroup(groupId, conn);
            Set<Long> targetGroupIds = group.labels.stream().map(l -> l.targetGroupId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!targetGroupIds.isEmpty() && "test".equals(group.type)) {
                for (Long targetGroupId : targetGroupIds) {
                    LabelGroup targetGroup = loadGroup(targetGroupId, conn);
                    if (targetGroup == null) {
                        System.out.println(group.name + " [" + group.id + "] missing target " + targetGroupId);
                        rtrn = false;
                    } else {
                        boolean hasSourceGroup = group.labels.stream()
                                .anyMatch(l -> Objects.equals(l.sourceGroupId, targetGroupId));
                        boolean allExist = targetGroup.labels.stream()
                                .allMatch(l -> group.labels.stream().anyMatch(g -> g.equals(l)));
                        if (!hasSourceGroup) {
                            System.out.println(group.name + " [" + group.id + "] missing labels from " + targetGroup.name + " ["
                                    + targetGroup.id + "]");
                            rtrn = false;
                        }
                        if (!allExist) {
                            System.out.println(group.name + " [" + group.id + "] missing label from target group "
                                    + targetGroup.name + " [" + targetGroup.id + "]");
                        }
                    }
                }
            }

            List<Label> selfTargeting = group.labels.stream().filter(l -> Objects.equals(l.groupId, l.targetGroupId)).toList();
            if (!selfTargeting.isEmpty()) {
                rtrn = false;
                selfTargeting.forEach(l -> {
                    System.out.println(group.name + " [" + group.id + "] label " + l.name + " is self targeting");
                });
            }
        }
        return rtrn;
    }

    //Used to sanity check group updates
    private static long countGroupLabels(long groupId, Connection conn) {
        try (PreparedStatement ps = conn
                .prepareStatement("select count(*) as count from exp_label where exp_label.group_id = ?")) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("count");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

    //Used to sanity check group updates
    private static long countGroupExtractors(long groupId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement(
                "select count(*) as count from exp_extractor left join exp_label on exp_extractor.parent_id = exp_label.id where exp_label.group_id = ?")) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("count");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

    //Used to sanity check label updates
    private static long countLabelExtractors(long labelId, Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("select count(*) as count from exp_extractor where parent_id = ?")) {
            ps.setLong(1, labelId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("count");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

    private static Map<Long, String> transformTargetUri(Connection conn) {
        Map<Long, String> rtrn = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "select t.id, t.targetschemauri,s.uri from transformer t left join schema s on t.schema_id = s.id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Long id = rs.getLong(1);
                String targetUri = rs.getString(2);
                String onwerUri = rs.getString(3);
                rtrn.put(id, targetUri != null && !targetUri.isBlank() ? targetUri : onwerUri);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return rtrn;
    }

    private static long countTotalExtractors(Connection conn) {
        try (PreparedStatement ps = conn.prepareStatement("select count(*) as count from exp_extractor");
                ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("count");

            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return -1;
    }

}
