package io.hyperfoil.tools.horreum.svc;

import io.hyperfoil.tools.horreum.api.SchemaService;
import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Schema;
import io.hyperfoil.tools.horreum.entity.json.SchemaExtractor;
import io.hyperfoil.tools.horreum.server.WithRoles;
import io.hyperfoil.tools.horreum.server.WithToken;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;

import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hibernate.Hibernate;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.uri.URIFactory;
import com.networknt.schema.uri.URIFetcher;
import com.networknt.schema.uri.URLFactory;

@WithRoles
public class SchemaServiceImpl implements SchemaService {
   private static final Logger log = Logger.getLogger(SchemaServiceImpl.class);

   private static final String UPDATE_TOKEN = "UPDATE schema SET token = ? WHERE id = ?";
   private static final String CHANGE_ACCESS = "UPDATE schema SET owner = ?, access = ? WHERE id = ?";
   private static final String FETCH_SCHEMAS_RECURSIVE = "WITH RECURSIVE refs(uri) AS (" +
         "SELECT ? UNION ALL " +
         "SELECT substring(jsonb_path_query(schema, '$.**.\"$ref\" ? (! (@ starts with \"#\"))')#>>'{}' from '[^#]*') as uri " +
            "FROM refs INNER JOIN schema on refs.uri = schema.uri) " +
         "SELECT schema.* FROM schema INNER JOIN refs ON schema.uri = refs.uri";

   private static final JsonSchemaFactory JSON_SCHEMA_FACTORY = new JsonSchemaFactory.Builder()
         .defaultMetaSchemaURI(JsonMetaSchema.getV4().getUri())
         .addMetaSchema(JsonMetaSchema.getV4())
         .addMetaSchema(JsonMetaSchema.getV6())
         .addMetaSchema(JsonMetaSchema.getV7())
         .addMetaSchema(JsonMetaSchema.getV201909()).build();
   private static final URIFactory URN_FACTORY = new URIFactory() {
      @Override
      public URI create(String uri) {
         return URI.create(uri);
      }

      @Override
      public URI create(URI baseURI, String segment) {
         throw new UnsupportedOperationException();
      }
   };
   private static final String[] ALL_URNS = Stream.concat(
         URLFactory.SUPPORTED_SCHEMES.stream(), Stream.of("urn")
   ).toArray(String[]::new);


   @Inject
   EntityManager em;

   @Inject
   SqlServiceImpl sqlService;

   @Inject
   SecurityIdentity identity;

   @WithToken
   @PermitAll
   @Override
   public Schema getSchema(int id, String token){
      Schema schema = Schema.find("id", id).firstResult();
      if (schema == null) {
         throw ServiceException.notFound("Schema not found");
      }
      return schema;
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public Integer add(Schema schema){
      Schema byName = Schema.find("name", schema.name).firstResult();
      if (byName != null) {
         if (Objects.equals(schema.id, byName.id)) {
            em.merge(schema);
         } else {
            throw ServiceException.serverError("Name already used");
         }
      } else {
         schema.id = null; //remove the id so we don't override an existing entry
         em.persist(schema);
      }
      em.flush();//manually flush to validate constraints
      return schema.id;
   }

   @Override
   public List<Schema> all(){
      return list(null,null,"name", Sort.Direction.Ascending);
   }

   @PermitAll
   @Override
   public List<Schema> list(Integer limit, Integer page, String sort, Sort.Direction direction) {
      if (sort == null || sort.isEmpty()) {
         sort = "name";
      }
      if (limit != null && page != null) {
         return Schema.findAll(Sort.by(sort).direction(direction)).page(Page.of(page, limit)).list();
      } else {
         return Schema.listAll(Sort.by(sort).direction(direction));
      }
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public String resetToken(Integer id) {
      return updateToken(id, Tokens.generateToken());
   }

   @RolesAllowed(Roles.TESTER)
   @Transactional
   @Override
   public String dropToken(Integer id) {
      return updateToken(id, null);
   }

   @Override
   public String updateToken(Integer id, String token) {
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
   @Transactional
   @Override
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   public void updateAccess(Integer id,
                            String owner,
                            int access) {
      if (access < Access.PUBLIC.ordinal() || access > Access.PRIVATE.ordinal()) {
         throw ServiceException.badRequest("Access not within bounds");
      }
      Query query = em.createNativeQuery(CHANGE_ACCESS);
      query.setParameter(1, owner);
      query.setParameter(2, access);
      query.setParameter(3, id);
      if (query.executeUpdate() != 1) {
         throw ServiceException.serverError("Access change failed (missing permissions?)");
      }
   }

   @PermitAll
   @Override
   public Collection<ValidationMessage> validate(JsonNode data, String schemaUri) {
      if (schemaUri == null || schemaUri.isEmpty()) {
         return null;
      }
      Query fetchSchemas = em.createNativeQuery(FETCH_SCHEMAS_RECURSIVE, Schema.class);
      fetchSchemas.setParameter(1, schemaUri);
      @SuppressWarnings("unchecked")
      Map<String, Schema> schemas = ((Stream<Schema>) fetchSchemas.getResultStream())
            .collect(Collectors.toMap(s -> s.uri, Function.identity()));
      Schema rootSchema = schemas.get(schemaUri);
      if (rootSchema == null || rootSchema.schema == null) {
         return null;
      }
      Set<ValidationMessage> errors;
      try {
         URIFetcher uriFetcher = uri -> new ByteArrayInputStream(schemas.get(uri.toString()).schema.toString().getBytes(StandardCharsets.UTF_8));

         JsonSchemaFactory factory = JsonSchemaFactory.builder(JSON_SCHEMA_FACTORY)
               .uriFactory(URN_FACTORY, "urn")
               .uriFetcher(uriFetcher, ALL_URNS).build();

         errors = factory.getSchema(rootSchema.schema).validate(data);
      } catch (Exception e) {
         // Do not let messed up schemas fail the upload
         log.warn("Schema validation failed", e);
         return null;
      }
      return errors;
   }

   @PermitAll
   @Override
   public List<SchemaExtractor> listExtractors(Integer schema) {
      List<SchemaExtractor> extractors;
      if (schema == null) {
         extractors = SchemaExtractor.<SchemaExtractor>findAll().stream().collect(Collectors.toList());
      } else {
         extractors = SchemaExtractor.<SchemaExtractor>find("schema_id", schema).stream().collect(Collectors.toList());
      }
      extractors.forEach(e -> {
         Hibernate.initialize(e.schema);
         em.detach(e);
         e.jsonpath = "$" + e.jsonpath;
      });
      return extractors;
   }

   static boolean nullOrEmpty(String str) {
      return str == null || str.isEmpty();
   }

   @RolesAllowed("tester")
   @Transactional
   @Override
   public void addOrUpdateExtractor(ExtractorUpdate update) {
      if (update == null) {
         throw ServiceException.badRequest("No extractor");
      }
      String accessor = update.accessor;
      String newName = nullOrEmpty(update.newName) ? accessor : update.newName;
      String jsonpath = update.jsonpath;

      if (nullOrEmpty(accessor) && !nullOrEmpty(newName)) {
         accessor = newName;
      }
      if (nullOrEmpty(accessor) || update.schema == null || jsonpath == null) {
         throw ServiceException.badRequest("Missing accessor/schema/jsonpath");
      }
      if (jsonpath.startsWith("strict ")) {
         jsonpath = jsonpath.substring(7);
      } else if (jsonpath.startsWith("lax ")) {
         jsonpath = jsonpath.substring(4);
      }
      if (jsonpath.startsWith("$")) {
         jsonpath = jsonpath.substring(1);
      }
      Schema persistedSchema = Schema.find("uri", update.schema).firstResult();
      if (persistedSchema == null) {
         throw ServiceException.badRequest("Missing schema " + update.schema);
      }
      SchemaExtractor extractor = SchemaExtractor.find("schema_id = ?1 and accessor = ?2", persistedSchema.id, accessor).firstResult();
      boolean isNew = false;
      if (extractor == null) {
         extractor = new SchemaExtractor();
         isNew = true;
         if (update.deleted) {
            throw ServiceException.notFound("Deleted extractor was not found");
         }
      } else if (update.deleted) {
         em.remove(extractor);
         return;
      }
      extractor.accessor = newName;
      extractor.schema = persistedSchema;
      extractor.jsonpath = jsonpath;
      if (isNew) {
         em.persist(extractor);
      }
   }

   @RolesAllowed("tester")
   @Transactional
   @Override
   public void delete(Integer id){
      Schema schema = Schema.find("id", id).firstResult();
      if (schema == null) {
         throw ServiceException.notFound("Schema not found");
      } else {
         SchemaExtractor.delete("schema_id", id);
         schema.delete();
      }
   }
}
