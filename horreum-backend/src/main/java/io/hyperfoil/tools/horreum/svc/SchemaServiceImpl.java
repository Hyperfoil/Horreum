package io.hyperfoil.tools.horreum.svc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.resource.InputStreamSource;
import com.networknt.schema.resource.SchemaLoader;
import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.api.data.Dataset;
import io.hyperfoil.tools.horreum.api.data.Extractor;
import io.hyperfoil.tools.horreum.api.data.Label;
import io.hyperfoil.tools.horreum.api.data.Schema;
import io.hyperfoil.tools.horreum.api.data.SchemaExport;
import io.hyperfoil.tools.horreum.api.data.Transformer;
import io.hyperfoil.tools.horreum.bus.AsyncEventChannels;
import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.hyperfoil.tools.horreum.entity.data.LabelDAO;
import io.hyperfoil.tools.horreum.entity.data.RunDAO;
import io.hyperfoil.tools.horreum.entity.data.SchemaDAO;
import io.hyperfoil.tools.horreum.entity.data.TransformerDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.hyperfoil.tools.horreum.mapper.DatasetMapper;
import io.hyperfoil.tools.horreum.mapper.LabelMapper;
import io.hyperfoil.tools.horreum.mapper.SchemaMapper;
import io.hyperfoil.tools.horreum.mapper.TransformerMapper;
import io.hyperfoil.tools.horreum.api.services.SchemaService;
import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.bus.BlockingTaskDispatcher;
import io.hyperfoil.tools.horreum.entity.ValidationErrorDAO;
import io.hyperfoil.tools.horreum.mapper.ValidationErrorMapper;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.server.WithToken;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.Query;
import jakarta.transaction.TransactionManager;
import jakarta.persistence.Tuple;
import jakarta.transaction.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.ws.rs.DefaultValue;
import org.hibernate.ScrollMode;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.SelectionQuery;
import org.hibernate.type.StandardBasicTypes;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.networknt.schema.JsonSchemaFactory;

public class SchemaServiceImpl implements SchemaService {
   private static final Logger log = Logger.getLogger(SchemaServiceImpl.class);

   //@formatter:off
   private static final String UPDATE_TOKEN = "UPDATE schema SET token = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE schema SET owner = ?, access = ? WHERE id = ?";
   private static final String FETCH_SCHEMAS_RECURSIVE =
         """
         WITH RECURSIVE refs(uri) AS
               (
                  SELECT ?
                  UNION ALL
                  SELECT substring(jsonb_path_query(schema, '$.**.\"$ref\" ? (! (@ starts with \"#\"))')#>>'{}' from '[^#]*'
               ) as uri
            FROM refs
            INNER JOIN schema on refs.uri = schema.uri)
         SELECT schema.*
         FROM schema
         INNER JOIN refs ON schema.uri = refs.uri
         """;
   //@formatter:on

   private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = new JsonSchemaFactory.Builder()
         .defaultMetaSchemaIri(JsonMetaSchema.getV4().getIri())
         .addMetaSchema(JsonMetaSchema.getV4())
         .addMetaSchema(JsonMetaSchema.getV6())
         .addMetaSchema(JsonMetaSchema.getV7())
         .addMetaSchema(JsonMetaSchema.getV201909()).build();
   private static final String[] ALL_URNS = new String[] {"urn", "uri", "http", "https", "ftp", "file", "jar"};

   @Inject
   EntityManager em;

   @Inject
   TransactionManager tm;

   @Inject
   SecurityIdentity identity;

   @Inject
   RunServiceImpl runService;

   @Inject
   ServiceMediator mediator;

   @Inject
   BlockingTaskDispatcher messageBus;
   @Inject
   Session session;

   @Inject
   ObjectMapper mapper;

   @WithToken
   @WithRoles
   @PermitAll
   @Override
   public Schema getSchema(int id, String token){
      SchemaDAO schema = SchemaDAO.find("id", id).firstResult();
      if (schema == null) {
         throw ServiceException.notFound("Schema not found");
      }
      return SchemaMapper.from(schema);
   }

   @Override
   public int idByUri(String uri) {
      try {
         return session.createNativeQuery("SELECT id FROM schema WHERE uri = ?", int.class).setParameter(1, uri).getSingleResult();
      } catch (NoResultException e) {
         throw ServiceException.notFound("Schema with given uri not found: " + uri);
      }
   }

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   @Override
   public Integer add(Schema schemaDTO){
      verifyNewSchema(schemaDTO);
      // Note: isEmpty is true for all non-object and non-array nodes
      if (schemaDTO.schema != null && schemaDTO.schema.isEmpty()) {
         schemaDTO.schema = null;
      }
      SchemaDAO schema = SchemaMapper.to(schemaDTO);
      if (schemaDTO.id != null && schemaDTO.id > 0) {
         SchemaDAO existing = SchemaDAO.findById(schema.id);
         if(existing == null)
            throw ServiceException.badRequest("An id was given, but it does not exist.");
         em.merge(schema);
         em.flush();
         if(!Objects.equals(schema.uri, existing.uri) ||
                 Objects.equals(schema.schema, existing.schema)) {
            //We need to delete from run_schemas and dataset_schemas as they will be recreated
            //when we create new datasets psql will still create new entries in dataset_schemas
            // https://github.com/Hyperfoil/Horreum/blob/master/horreum-backend/src/main/resources/db/changeLog.xml#L2522
            em.createNativeQuery("DELETE FROM run_schemas WHERE schemaid = ?1")
                    .setParameter(1, schema.id).executeUpdate();
            em.createNativeQuery("DELETE FROM dataset_schemas WHERE schema_id = ?1")
                    .setParameter(1, schema.id).executeUpdate();
            newOrUpdatedSchema(schema);
         }
      }
      else {
         schema.id = null;
         schema.persist();
         em.flush();
         newOrUpdatedSchema(schema);
      }
      log.debugf("Added schema %s (%d), URI %s", schema.name, schema.id, schema.uri);
      return schema.id;
   }

   private void newOrUpdatedSchema(SchemaDAO schema) {
      log.debugf("Push schema event for async run schemas update: %d (%s)", schema.id, schema.uri);
      Util.registerTxSynchronization(tm, txStatus -> mediator.queueSchemaSync(schema.id));
   }

   private void verifyNewSchema(Schema schemaDTO) {
      if (schemaDTO.uri == null || Arrays.stream(ALL_URNS).noneMatch(scheme -> schemaDTO.uri.startsWith(scheme + ":"))) {
         throw ServiceException.badRequest("Please use URI starting with one of these schemes: " + Arrays.toString(ALL_URNS));
      }
      SchemaDAO byName = SchemaDAO.find("name", schemaDTO.name).firstResult();
      if (byName != null && !Objects.equals(byName.id, schemaDTO.id)) {
         throw ServiceException.badRequest("Name already used");
      }
      SchemaDAO byUri = SchemaDAO.find("uri", schemaDTO.uri).firstResult();
      if (byUri != null && !Objects.equals(byUri.id, schemaDTO.id)) {
         throw ServiceException.badRequest("URI already used");
      }
   }

   @PermitAll
   @WithRoles
   @Override
   public SchemaQueryResult list(Integer limit, Integer page, String sort,
                                 @DefaultValue("Ascending")  SortDirection direction) {
      if (sort == null || sort.isEmpty()) {
         sort = "name";
      }
      Sort.Direction sortDirection = direction == null ? null : Sort.Direction.valueOf(direction.name());
      if (limit != null && page != null) {
         List<SchemaDAO> schemas = SchemaDAO.findAll(Sort.by(sort).direction(sortDirection)).page(Page.of(page, limit)).list();
         return new SchemaQueryResult( schemas.stream().map(SchemaMapper::from).collect(Collectors.toList()), SchemaDAO.count());
      } else {
         List<SchemaDAO> schemas = SchemaDAO.listAll(Sort.by(sort).direction(sortDirection));
         return new SchemaQueryResult( schemas.stream().map(SchemaMapper::from).collect(Collectors.toList()), SchemaDAO.count());
      }
   }

   @WithRoles
   @Override
   public List<SchemaDescriptor> descriptors(List<Integer> ids) {
      String sql = "SELECT id, name, uri FROM schema";
      if (ids != null && !ids.isEmpty()) {
         sql += " WHERE id IN ?1";
      }
      SelectionQuery<SchemaDescriptor> query = session.createNativeQuery(sql, Tuple.class)
              .setTupleTransformer((tuple, aliases) -> {
                 SchemaDescriptor sd = new SchemaDescriptor();
                 sd.id = (int) tuple[0];
                 sd.name = (String) tuple[1];
                 sd.uri = (String) tuple[2];
                 return sd;
              });
      if (ids != null && !ids.isEmpty()) {
         query.setParameter(1, ids);
      }
      return query.getResultList();
   }

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   @Override
   public String resetToken(int id) {
      return updateToken(id, Tokens.generateToken());
   }

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   @Override
   public void dropToken(int id) {
      updateToken(id, null);
   }

   public String updateToken(int id, String token) {
      Query query = em.createNativeQuery(UPDATE_TOKEN);
      query.setParameter(1, token);
      query.setParameter(2, id);
      if (query.executeUpdate() != 1) {
         throw ServiceException.serverError("Token reset failed (missing permissions?)");
      } else {
         return token;
      }
   }

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   @Override
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public void updateAccess(int id, String owner, Access access) {
      if (access.ordinal() < Access.PUBLIC.ordinal() || access.ordinal() > Access.PRIVATE.ordinal()) {
         throw ServiceException.badRequest("Access not within bounds");
      }
      Query query = em.createNativeQuery(CHANGE_ACCESS);
      query.setParameter(1, owner);
      query.setParameter(2, access);
      query.setParameter(3, id);
      try {
         if (query.executeUpdate() != 1) {
            throw ServiceException.serverError("Access change failed (missing permissions?)");
         }
      }
      catch (Exception e) {
         throw ServiceException.serverError("Access change failed (missing permissions?) "+e.getMessage());
      }
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   void validateRunData(int runId, Predicate<String> schemaFilter) {
      log.debugf("About to validate data for run %d", runId);
      RunDAO run = RunDAO.findById(runId);
      if (run == null) {
         log.errorf("Cannot load run %d for schema validation", runId);
         return;
      }
      // remember to clear prev validation errors
      em.createNativeQuery("DELETE FROM run_validationerrors WHERE run_id = ?1")
              .setParameter(1, runId).executeUpdate();

      if(run.validationErrors != null)
         run.validationErrors.removeIf(e -> schemaFilter == null || schemaFilter.test(e.schema.uri));
      if(run.validationErrors == null)
         run.validationErrors = new ArrayList<>();
      validateData(run.data, schemaFilter, run.validationErrors);
      if (run.metadata != null) {
         validateData(run.metadata, schemaFilter, run.validationErrors);
      }
      run.persist();
      if(mediator.testMode())
         Util.registerTxSynchronization(tm, txStatus -> mediator.publishEvent(AsyncEventChannels.RUN_VALIDATED, run.testid,
              new Schema.ValidationEvent(run.id, run.validationErrors.stream().map(ValidationErrorMapper::fromValidationError).collect(Collectors.toList()) )));

      ;
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional
   void validateDatasetData(int datasetId, Predicate<String> schemaFilter) {
      log.debugf("About to validate data for dataset %d", datasetId);
      DatasetDAO dataset = DatasetDAO.findById(datasetId);
      if (dataset == null) {
         // Don't log error when the dataset is not present and we're revalidating all datasets - it might be
         // concurrently removed because of URI change
         if (schemaFilter != null) {
            log.errorf("Cannot load dataset %d for schema validation", datasetId);
         }
         return;
      }
      em.createNativeQuery("DELETE FROM dataset_validationerrors WHERE dataset_id = ?1")
              .setParameter(1, dataset.id).executeUpdate();
      if(dataset.data == null)
         return;
      if(dataset.validationErrors != null)
         dataset.validationErrors.removeIf(e -> schemaFilter == null || (e.schema != null && schemaFilter.test(e.schema.uri)));
      if(dataset.data != null) {
         if(dataset.validationErrors == null)
            dataset.validationErrors = new ArrayList<>();
         validateData(dataset.data, schemaFilter, dataset.validationErrors);
         for (var item : dataset.data) {
            String uri = item.path("$schema").asText();
            if (uri == null || uri.isBlank()) {
               ValidationErrorDAO error = new ValidationErrorDAO();
               error.error = JsonNodeFactory.instance.objectNode().put("type", "No schema").put("message", "Element in the dataset does not reference any schema through the '$schema' property.");
               dataset.validationErrors.add(error);
            }
         }
         dataset.persist();
      }

      if(mediator.testMode())
         Util.registerTxSynchronization(tm, txStatus -> mediator.publishEvent(AsyncEventChannels.DATASET_VALIDATED, dataset.testid, new Schema.ValidationEvent(dataset.id, DatasetMapper.from(dataset).validationErrors )));
   }

   @WithRoles(extras = Roles.HORREUM_SYSTEM)
   @Transactional(Transactional.TxType.REQUIRES_NEW)
   @TransactionConfiguration(timeout = 3600) // 1 hour, this may run a long time
   void revalidateAll(int schemaId) {
      SchemaDAO schema = SchemaDAO.findById(schemaId);
      if (schema == null) {
         log.errorf("Cannot load schema %d for validation", schemaId);
         return;
      }
      //clear tables on schemaId
      em.createNativeQuery("DELETE FROM dataset_validationerrors WHERE schema_id = ?1")
              .setParameter(1, schemaId).executeUpdate();
      em.createNativeQuery("DELETE FROM run_validationerrors WHERE schema_id = ?1")
              .setParameter(1, schemaId).executeUpdate();

      Predicate<String> schemaFilter = uri -> uri.equals(schema.uri);
      // If the URI was updated together with JSON schema run_schemas are removed and filled-in asynchronously
      // so we cannot rely on run_schemas
      runService.findRunsWithUri(schema.uri, (runId, testId) ->
         messageBus.executeForTest(testId, () -> validateRunData(runId, schemaFilter))
      );
      // Datasets might be re-created if URI is changing, so we might work on old, non-existent ones
      ScrollableResults<RecreateDataset> results = session
              .createNativeQuery("SELECT id, testid FROM dataset WHERE ?1 IN (SELECT jsonb_array_elements(data)->>'$schema')", Tuple.class).setParameter(1, schema.uri)
              .setTupleTransformer((tuple, aliases) -> {
                 RecreateDataset r = new RecreateDataset();
                 r.datasetId = (int) tuple[0];
                 r.testId = (int) tuple[1];
                 return r;
              })
              .setReadOnly(true).setFetchSize(100)
              .scroll(ScrollMode.FORWARD_ONLY);
      while (results.next()) {
         RecreateDataset r = results.get();
         messageBus.executeForTest(r.testId, () -> validateDatasetData(r.datasetId, schemaFilter));
      }
   }

   private void validateData(JsonNode data, Predicate<String> filter, Collection<ValidationErrorDAO> consumer) {
      Map<String, List<JsonNode>> toCheck = new HashMap<>();
      addIfHasSchema(toCheck, data);
      for (JsonNode child : data) {
         addIfHasSchema(toCheck, child);
      }

      for (String schemaUri: toCheck.keySet()) {
         if (filter != null && !filter.test(schemaUri)) {
            continue;
         }
         NativeQuery<SchemaDAO> fetchSchemas = session.createNativeQuery(FETCH_SCHEMAS_RECURSIVE, SchemaDAO.class);
         fetchSchemas.setParameter(1, schemaUri);
         Map<String, SchemaDAO> schemas = fetchSchemas.getResultStream()
               .collect(Collectors.toMap(s -> s.uri, Function.identity()));

         // this is root in the sense of JSON schema referencing other schemas, NOT Horreum first-level schema
         SchemaDAO rootSchema = schemas.get(schemaUri);
         if (rootSchema == null || rootSchema.schema == null) {
            continue;
         }

         try {
            HorreumURIFetcher fetcher = new HorreumURIFetcher();
            fetcher.addResource(SchemaLocation.of(schemaUri).getAbsoluteIri(), rootSchema.schema.toString());

            JsonSchemaFactory factory = JsonSchemaFactory.builder(JSON_SCHEMA_FACTORY)
                    .schemaLoaders( schemaLoaders -> schemaLoaders.add(fetcher))
                    .build();

            for (JsonNode node : toCheck.get(schemaUri)) {
               factory.getSchema(rootSchema.schema).validate(node).forEach(msg -> {
                  ValidationErrorDAO error = new ValidationErrorDAO();
                  error.schema = rootSchema;
                  error.error = Util.OBJECT_MAPPER.valueToTree(msg);
                  if(!consumer.contains(error))
                     consumer.add(error);
               });
            }
         } catch (Throwable e) {
            // Do not let messed up schemas fail the upload
            log.error("Schema validation failed", e);
            ValidationErrorDAO error = new ValidationErrorDAO();
            error.schema = rootSchema;
            error.error = JsonNodeFactory.instance.objectNode().put("type", "Execution error").put("message", e.getMessage());
            if(!consumer.contains(error))
               consumer.add(error);
         }
         log.debug("Validation completed");
      }
   }

   private void addIfHasSchema(Map<String, List<JsonNode>> toCheck, JsonNode node) {
      String uri = node.path("$schema").asText();
      if (uri != null && !uri.isBlank()) {
         toCheck.computeIfAbsent(uri, u -> new ArrayList<>()).add(node);
      }
   }

   @RolesAllowed("tester")
   @WithRoles
   @Transactional
   @Override
   public void delete(int id){
      SchemaDAO schema = SchemaDAO.find("id", id).firstResult();
      if (schema == null) {
         throw ServiceException.notFound("Schema not found");
      } else {
         log.debugf("Deleting schema %s (%d), URI %s", schema.name, schema.id, schema.uri);
         em.createNativeQuery("DELETE FROM label_extractors WHERE label_id IN (SELECT id FROM label WHERE schema_id = ?1)")
               .setParameter(1, id).executeUpdate();
         updateLabelsForDelete(id);
         em.createNativeQuery("DELETE FROM transformer_extractors WHERE transformer_id IN (SELECT id FROM transformer WHERE schema_id = ?1)")
               .setParameter(1, id).executeUpdate();
         em.createNativeQuery("DELETE FROM test_transformers WHERE transformer_id IN (SELECT id FROM transformer WHERE schema_id = ?1)")
               .setParameter(1, id).executeUpdate();
         TransformerDAO.delete("schema.id", id);
         em.createNativeQuery("DELETE FROM run_schemas WHERE schemaid = ?1").setParameter(1, id).executeUpdate();
         em.createNativeQuery("DELETE FROM dataset_schemas WHERE schema_id = ?1").setParameter(1, id).executeUpdate();
         schema.delete();
      }
   }

   private void updateLabelsForDelete(int schemaId) {
      for(LabelDAO label : LabelDAO.<LabelDAO>list( "schema.id = ?1",schemaId)) {
         doUpdateLabelForDelete(label);
      }
   }

   private void doUpdateLabelForDelete(LabelDAO label) {
      em.createNativeQuery("DELETE FROM label_values WHERE label_id = ?1").setParameter(1, label.id).executeUpdate();
      int schemaId = label.getSchemaId();
      int labelId = label.id;
      label.delete();
      emitLabelChanged(labelId, schemaId);
   }

   @PermitAll
   @WithRoles
   @Override
   public List<LabelLocation> findUsages(String label) {
      if (label == null) {
         throw ServiceException.badRequest("No label");
      }
      label = label.trim();
      List<LabelLocation> result = new ArrayList<>();
      for (Object row: em.createNativeQuery("SELECT id, name FROM test WHERE json_contains(fingerprint_labels, ?1)")
            .setParameter(1, label).getResultList()) {
         Object[] columns = (Object[]) row;
         result.add(new LabelInFingerprint((int) columns[0], (String) columns[1]));
      }
      for (Object row: em.createNativeQuery("SELECT test.id as testid, test.name as testname, mdr.id, mdr.name FROM missingdata_rule mdr JOIN test ON mdr.test_id = test.id WHERE json_contains(mdr.labels, ?1)")
            .setParameter(1, label).getResultList()) {
         Object[] columns = (Object[]) row;
         result.add(new LabelInRule((int) columns[0], (String) columns[1], (int) columns[2], (String) columns[3]));
      }
      for (Object row: em.createNativeQuery("SELECT test.id as testid, test.name as testname, v.id as varid, v.name as varname FROM variable v " +
            "JOIN test ON test.id = v.testid WHERE json_contains(v.labels, ?1)")
            .setParameter(1, label).getResultList()) {
         Object[] columns = (Object[]) row;
         result.add(new LabelInVariable((int) columns[0], (String) columns[1], (int) columns[2], (String) columns[3]));
      }
      for (Object row: em.createNativeQuery("SELECT test.id as testid, test.name as testname, view.id as viewid, view.name as viewname, vc.id as componentid, vc.headername FROM viewcomponent vc " +
            "JOIN view ON vc.view_id = view.id JOIN test ON test.id = view.test_id WHERE json_contains(vc.labels, ?1)")
            .setParameter(1, label).getResultList()) {
         Object[] columns = (Object[]) row;
         result.add(new LabelInView((int) columns[0], (String) columns[1], (int) columns[2], (String) columns[3], (int) columns[4], (String) columns[5]));
      }
      for (Object row: em.createNativeQuery("SELECT test.id as testid, test.name as testname, trc.id as configid, trc.title, " +
            "filterlabels, categorylabels, serieslabels, scalelabels FROM tablereportconfig trc JOIN test ON test.id = trc.testid " +
            "WHERE json_contains(filterlabels, ?1) OR json_contains(categorylabels, ?1) OR json_contains(serieslabels, ?1) OR json_contains(scalelabels, ?1);")
            .setParameter(1, label).unwrap(NativeQuery.class)
            .addScalar("testid", StandardBasicTypes.INTEGER )
            .addScalar("testname", StandardBasicTypes.TEXT )
            .addScalar("configid", StandardBasicTypes.INTEGER)
            .addScalar("title", StandardBasicTypes.TEXT)
            .addScalar("filterlabels", JsonBinaryType.INSTANCE)
            .addScalar("categorylabels", JsonBinaryType.INSTANCE)
            .addScalar("serieslabels", JsonBinaryType.INSTANCE)
            .addScalar("scalelabels", JsonBinaryType.INSTANCE)
            .getResultList()) {
         Object[] columns = (Object[]) row;
         StringBuilder where = new StringBuilder();
         if ( columns[4] != null) addPart(where, (ArrayNode) columns[4], label, "filter");
         if ( columns[5] != null) addPart(where, (ArrayNode) columns[5], label, "series");
         if ( columns[6] != null) addPart(where, (ArrayNode) columns[6], label, "category");
         if ( columns[7] != null) addPart(where, (ArrayNode) columns[7], label, "label");
         result.add(new LabelInReport((int) columns[0], (String) columns[1], (int) columns[2], (String) columns[3], where.toString(), null));
      }
      for (Object row: em.createNativeQuery("SELECT test.id as testid, test.name as testname, trc.id as configid, trc.title, rc.name FROM reportcomponent rc " +
            "JOIN tablereportconfig trc ON rc.reportconfig_id = trc.id JOIN test ON test.id = trc.testid " +
            "WHERE json_contains(rc.labels, ?1)")
            .setParameter(1, label).getResultList()) {
         Object[] columns = (Object[]) row;
         result.add(new LabelInReport((int) columns[0], (String) columns[1], (int) columns[2], (String) columns[3], "component", (String) columns[4]));
      }
      return result;
   }

   @PermitAll
   @WithRoles
   @Override
   public List<Transformer> listTransformers(int schemaId) {
      List<TransformerDAO> transformers = TransformerDAO.find("schema.id", Sort.by("name"), schemaId).list();
      return transformers.stream().map(TransformerMapper::from).collect(Collectors.toList());
   }

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   @Override
   public int addOrUpdateTransformer(int schemaId, Transformer transformerDTO) {
      if (!identity.hasRole(transformerDTO.owner)) {
         throw ServiceException.forbidden("This user is not a member of team " + transformerDTO.owner);
      }
      if (transformerDTO.extractors == null) {
         // Transformer without an extractor is an edge case, but replacing the schema with explicit null/undefined could make sense.
         transformerDTO.extractors = Collections.emptyList();
      }
      if (transformerDTO.name == null || transformerDTO.name.isBlank()) {
         throw ServiceException.badRequest("Transformer must have a name!");
      }
      validateExtractors(transformerDTO.extractors);
      TransformerDAO transformer = TransformerMapper.to(transformerDTO);
      if (transformer.id == null || transformer.id < 0) {
         transformer.id = null;
         transformer.schema = em.getReference(SchemaDAO.class, schemaId);
         transformer.persistAndFlush();
      } else {
         TransformerDAO existing = TransformerDAO.findById(transformer.id);
         if (!Objects.equals(existing.schema.id, schemaId)) {
            throw ServiceException.badRequest("Transformer id=" + transformer.id + ", name=" + existing.name +
                  " belongs to a different schema: " + existing.schema.id + "(" + existing.schema.uri + ")");
         }
         if (!identity.hasRole(existing.owner)) {
            throw ServiceException.forbidden("Cannot transfer ownership: this user is not a member of team " + existing.owner);
         }
         existing.name = transformer.name;
         existing.description = transformer.description;
         existing.owner = transformer.owner;
         existing.access = transformer.access;
         existing.targetSchemaUri = transformer.targetSchemaUri;
         existing.function = transformer.function;
         existing.extractors.clear();
         existing.extractors.addAll(transformer.extractors);
         existing.persist();
      }
      return transformer.id;
   }

   private void validateExtractors(Collection<Extractor> extractors) {
      for (Extractor extractor : extractors) {
         if (extractor.name == null || extractor.name.isBlank()) {
            throw ServiceException.badRequest("One of the extractors does not have a name!");
         } else if (extractor.jsonpath == null || extractor.jsonpath.isBlank()) {
            throw ServiceException.badRequest("One of the extractors is missing JSONPath!");
         }
      }
   }

   @RolesAllowed(Roles.TESTER)
   @WithRoles
   @Transactional
   @Override
   public void deleteTransformer(int schemaId, int transformerId) {
      TransformerDAO t = TransformerDAO.findById(transformerId);
      if (t == null) {
         throw ServiceException.notFound("Transformer " + transformerId + " not found");
      }
      if (t.schema.id != schemaId) {
         throw ServiceException.badRequest("Transformer " + transformerId + " does not belong to schema " + schemaId);
      }
      String testerRole = t.owner.substring(0, t.owner.length() - 5) + "-tester";
      if (!identity.hasRole(testerRole)) {
         throw ServiceException.forbidden("You are not an owner of transfomer " + transformerId + "(" + t.owner + "); missing role " + testerRole + ", available roles: " + identity.getRoles());
      }
      List<Object[]> testsUsingTransformer = session
              .createNativeQuery("SELECT test.id, test.name FROM test_transformers JOIN test ON test_id = test.id WHERE transformer_id = ?1", Object[].class)
            .setParameter(1, transformerId).getResultList();
      if (!testsUsingTransformer.isEmpty()) {
         throw ServiceException.badRequest("This transformer is still referenced in some tests: " +
         testsUsingTransformer.stream().map(row -> {
            int id = (int) row[0];
            String name = (String) row[1];
            return "<a href=\"/test/" + id + "\">" + name + "</a>";
         }).collect(Collectors.joining(", ")) + "; please remove them before deleting it.");
      }
      t.delete();
   }

   @WithRoles
   @Override
   public List<Label> labels(int schemaId) {
      List<LabelDAO> labels = LabelDAO.find("schema.id", schemaId).list();
      return labels.stream().map(LabelMapper::from).collect(Collectors.toList());
   }

   @WithRoles
   @Transactional
   @Override
   public Integer addOrUpdateLabel(int schemaId, Label labelDTO) {
      if (labelDTO == null) {
         throw ServiceException.badRequest("No label?");
      }
      if (!identity.hasRole(labelDTO.owner)) {
         throw ServiceException.forbidden("This user is not a member of team " + labelDTO.owner);
      }
      if (labelDTO.name == null || labelDTO.name.isBlank()) {
         throw ServiceException.badRequest("Label must have a non-blank name");
      }
      validateExtractors(labelDTO.extractors);

      LabelDAO label = LabelMapper.to(labelDTO);
      if (label.id == null || label.id < 0) {
         label.id = null;
         label.schema = em.getReference(SchemaDAO.class, schemaId);
         checkSameName(label);
         label.persistAndFlush();
         emitLabelChanged(label.id, schemaId);
      } else {
         LabelDAO existing = LabelDAO.findById(label.id);
         if (existing == null) {
            label.id = -1;
            existing = label;
         }
         if (!Objects.equals(existing.schema.id, schemaId)) {
            throw ServiceException.badRequest("Label id=" + label.id + ", name=" + existing.name +
                  " belongs to a different schema: " + existing.schema.id + "(" + existing.schema.uri + ")");
         }
         if (!identity.hasRole(existing.owner)) {
            throw ServiceException.forbidden("Cannot transfer ownership: this user is not a member of team " + existing.owner);
         }
         if (!existing.name.equals(label.name)) {
            checkSameName(label);
         }
         existing.name = label.name;
         //When we clear extractors we should also delete label_values
         if(existing.id > 0) {
            em.createNativeQuery("DELETE FROM dataset_view WHERE dataset_id IN (SELECT dataset_id FROM label_values WHERE label_id = ?1)").setParameter(1, existing.id).executeUpdate();
            em.createNativeQuery("DELETE FROM label_values WHERE label_id = ?1").setParameter(1, existing.id).executeUpdate();
         }
         existing.extractors.clear();
         existing.extractors.addAll(label.extractors);
         existing.function = label.function;
         existing.owner = label.owner;
         existing.access = label.access;
         existing.filtering = label.filtering;
         existing.metrics = label.metrics;
         existing.persistAndFlush();

         emitLabelChanged(existing.id, existing.getSchemaId());
      }
      return label.id;
   }

   private void emitLabelChanged(int labelId, int schemaId) {
      String datasetIdQuery = """
              SELECT ds.id, ds.testId
              from dataset_schemas
              LEFT JOIN dataset ds on ds.id = dataset_schemas.dataset_id
              WHERE schema_id = ?1
              ORDER BY dataset_id DESC;
              """;

      try {
         List<Object[]> datasetIds = session
                 .createNativeQuery(datasetIdQuery)
                 .setParameter(1, schemaId)
                 .getResultList();
         if (datasetIds == null || datasetIds.isEmpty() ) {
            log.debug("Could not extract datasetIds from dataset_schemas with schemaId="+schemaId);
            return;
         }

         for(var dataset : datasetIds) {
            Util.registerTxSynchronization(tm, txStatus -> mediator.queueDatasetEvents(new Dataset.EventNew((Integer) dataset[0], (Integer) dataset[1], 0, labelId, true)));
         }
      }
      catch (NoResultException nre) {
        log.debug("Could not find datasetId/testId to recalculate labels: "+nre.getMessage());
      }
   }

   private void checkSameName(LabelDAO label) {
      LabelDAO sameName = LabelDAO.find("schema = ?1 AND name = ?2", label.schema, label.name).firstResult();
      if (sameName != null) {
         throw ServiceException.badRequest("There is an existing label with the same name (" + label.name + ") in this schema; please choose different name.");
      }
   }

   @WithRoles
   @Transactional
   @Override
   public void deleteLabel(int schemaId, int labelId) {
      LabelDAO label = LabelDAO.findById(labelId);
      if (label == null) {
         throw ServiceException.notFound("Label " + labelId + " not found");
      }
      if (label.schema.id != schemaId) {
         throw ServiceException.badRequest("Label " + labelId + " does not belong to schema " + schemaId);
      }
      String testerRole = label.owner.substring(0, label.owner.length() - 5) + "-tester";
      if (!identity.hasRole(testerRole)) {
         throw ServiceException.forbidden("You are not an owner of label " + labelId + "(" + label.owner + "); missing role " + testerRole + ", available roles: " + identity.getRoles());
      }
      doUpdateLabelForDelete(label);
   }

   @PermitAll
   @WithRoles
   @Override
   public Collection<LabelInfo> allLabels(String filterName) {
      String sqlQuery = "SELECT label.name, label.metrics, label.filtering, schema.id, schema.name as schemaName, schema.uri FROM label JOIN schema ON schema.id = label.schema_id";
      if (filterName != null && !filterName.isBlank()) {
         sqlQuery += " WHERE label.name = ?1";
      }
      NativeQuery<Object[]> query = session.createNativeQuery(sqlQuery, Object[].class);
      if (filterName != null) {
         query.setParameter(1, filterName.trim());
      }
      List<Object[]> rows = query.getResultList();
      Map<String, LabelInfo> labels = new TreeMap<>();
      for (Object[] row : rows) {
         String name = (String) row[0];
         LabelInfo info = labels.computeIfAbsent(name, LabelInfo::new);
         info.metrics = info.metrics || (boolean) row[1];
         info.filtering = info.filtering || (boolean) row[2];
         int schemaId = (int) row[3];
         String schemaName = (String) row[4];
         String uri = (String) row[5];
         info.schemas.add(new SchemaDescriptor(schemaId, schemaName, uri));
      }
      return labels.values();
   }

   @PermitAll
   @WithRoles
   @Override
   public List<TransformerInfo> allTransformers() {
      List<TransformerInfo> transformers = new ArrayList<>();
      List<Object[]> rows = session.createNativeQuery(
            "SELECT s.id as sid, s.uri, s.name as schemaName, t.id as tid, t.name as transformerName FROM schema s JOIN transformer t ON s.id = t.schema_id", Object[].class).getResultList();
      for (Object[] row: rows) {
         TransformerInfo info = new TransformerInfo();
         info.schemaId = (int) row[0];
         info.schemaUri = (String) row[1];
         info.schemaName = (String) row[2];
         info.transformerId = (int) row[3];
         info.transformerName = (String)  row[4];
         transformers.add(info);
      }
      return transformers;
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @WithRoles
   @Transactional
   @Override
   public SchemaExport exportSchema(int id) {
      SchemaDAO schema = SchemaDAO.findById(id);
      if (schema == null) {
         throw ServiceException.notFound("Schema not found");
      }
      SchemaExport export = new SchemaExport(SchemaMapper.from(schema));

      export.labels = LabelDAO.<LabelDAO>list("schema.id", schema.id).stream().map(LabelMapper::from).collect(Collectors.toList());
      export.transformers = TransformerDAO.<TransformerDAO>list("schema.id", schema.id).stream().map(TransformerMapper::from).collect(Collectors.toList());

      return export;
   }

   @RolesAllowed({Roles.TESTER, Roles.ADMIN})
   @WithRoles
   @Transactional
   @Override
   public void importSchema(ObjectNode node) {
      SchemaExport importSchema = Util.OBJECT_MAPPER.convertValue(node, SchemaExport.class);
      boolean newSchema = true;
      SchemaDAO schema = null;
      if ( importSchema.id != null ) {
         //first check if this schema exists
         schema = SchemaDAO.findById(importSchema.id);
         if(schema != null) {
            em.merge(SchemaMapper.to(importSchema));
            newSchema = false;
         }
         else {
            verifyNewSchema(importSchema);
            schema = SchemaMapper.to(importSchema);
            schema.id = null;
            schema.persist();
         }
      }
      else {
         verifyNewSchema(importSchema);
         schema = SchemaMapper.to(importSchema);
         em.persist(schema);
      }
      if (importSchema.labels != null && !importSchema.labels.isEmpty()) {
         for (Label l : importSchema.labels) {
                LabelDAO label = LabelMapper.to(l);
               label.schema = schema;
               if ( label.id != null && !newSchema){
                  em.merge(label);
               } else {
                  label.id = null;
                  label.schema = schema;
                  em.persist(label);
               }
         }
      }
      if (importSchema.transformers != null && !importSchema.transformers.isEmpty()) {
         for (Transformer t : importSchema.transformers) {
            TransformerDAO transformer = TransformerMapper.to(t);
               transformer.schema = schema;
               if(transformer.id == null || transformer.id < 1) {
                  em.persist(transformer);
               }
               else {
                  if(TransformerDAO.findById(transformer.id) != null) {
                     transformer.schema = schema;
                     em.merge(transformer);
                  }
                  else {
                     transformer.id = null;
                     transformer.schema = schema;
                     em.persist(transformer);
                  }
               }
         }
      }
      //let's wrap flush in a try/catch, if we get any role issues at commit we can give a sane msg
      try {
         em.flush();
      }
      catch (Exception e) {
         throw ServiceException.serverError("Failed to persist Schema: "+e.getMessage());
      }
   }

   private void addPart(StringBuilder where, ArrayNode column, String label, String type) {
      for (JsonNode col : column) {
         if (label.equals(col.textValue())) {
            if (!where.isEmpty()) {
               where.append(", ");
            }
            where.append(type);
            return;
         }
      }
   }

   class RecreateDataset {
      private int datasetId;
      private int testId;
   }

   private class HorreumURIFetcher implements SchemaLoader {

      private Map<AbsoluteIri, InputStream> uriToResource = new HashMap<>();

      void addResource(AbsoluteIri uri, String schema) {
         addResource(uri, new ByteArrayInputStream(schema.getBytes(StandardCharsets.UTF_8)));
      }

      void addResource(AbsoluteIri uri, InputStream is) {
         uriToResource.put(uri, is);
      }

      @Override
      public InputStreamSource getSchema(AbsoluteIri absoluteIri) {
         return () -> uriToResource.get(absoluteIri);
      }
   }
}
