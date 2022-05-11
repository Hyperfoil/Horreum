package io.hyperfoil.tools.horreum.api;

import java.util.Collection;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Hook;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.TestToken;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.quarkus.panache.common.Sort;

@Path("/api/test")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public interface TestService {
   @DELETE
   @Path("{id}")
   void delete(@PathParam("id") Integer id);

   @GET
   @Path("{id}")
   Test get(@PathParam("id") Integer id, @QueryParam("token") String token);

   @GET
   @Path("byName/{name}")
   Test getByNameOrId(@PathParam("name") String input);

   @POST
   Test add(Test test);

   @GET
   List<Test> list(@QueryParam("roles") String roles,
                   @QueryParam("limit") Integer limit,
                   @QueryParam("page") Integer page,
                   @QueryParam("sort") @DefaultValue("name") String sort,
                   @QueryParam("direction") @DefaultValue("Ascending") Sort.Direction direction);

   @Path("summary")
   @GET
   TestListing summary(@QueryParam("roles") String roles, @QueryParam("folder") String folder);

   @Path("folders")
   @GET
   List<String> folders(@QueryParam("roles") String roles);

   @POST
   @Path("{id}/addToken")
   @Produces(MediaType.TEXT_PLAIN)
   Integer addToken(@PathParam("id") Integer testId, TestToken token);

   @GET
   @Path("{id}/tokens")
   Collection<TestToken> tokens(@PathParam("id") Integer testId);

   @POST
   @Path("{id}/revokeToken/{tokenId}")
   void dropToken(@PathParam("id") Integer testId, @PathParam("tokenId") Integer tokenId);

   @POST
   @Path("{id}/updateAccess")
   // TODO: it would be nicer to use @FormParams but fetchival on client side doesn't support that
   void updateAccess(@PathParam("id") Integer id,
                     @QueryParam("owner") String owner,
                     @QueryParam("access") Access access);

   @POST
   @Path("{testId}/view")
   void updateView(@PathParam("testId") Integer testId, View view);

   @POST
   @Consumes // any
   @Path("{id}/notifications")
   void updateAccess(@PathParam("id") Integer id,
                     @QueryParam("enabled") boolean enabled);

   @POST
   @Path("{id}/move")
   void updateFolder(@PathParam("id") Integer id, @QueryParam("folder") String folder);

   @POST
   @Path("{testId}/hook")
   void updateHook(@PathParam("testId") Integer testId, Hook hook);

   @GET
   @Path("{id}/fingerprint")
   List<JsonNode> listFingerprints(@PathParam("id") int testId);

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   @Path("{id}/transformers")
   void updateTransformers(@PathParam("id") Integer testId, List<Integer> transformerIds);

   @POST
   @Path("{id}/fingerprint")
   void updateFingerprint(@PathParam("id") int testId, FingerprintUpdate update);

   @POST
   @Path("{id}/recalculate")
   void recalculateDatasets(@PathParam("id") int testId);

   @GET
   @Path("{id}/recalculate")
   RecalculationStatus getRecalculationStatus(@PathParam("id") int testId);

   class TestListing {
      public List<TestSummary> tests;
   }

   class TestSummary {
      public int id;
      public String name;
      public String folder;
      public String description;
      // SQL count(*) returns BigInteger
      public Number datasets;
      public Number runs;
      public String owner;
      public int access;
   }

   class FingerprintUpdate {
      public List<String> labels;
      public String filter;
   }

   class RecalculationStatus {
      public long timestamp;
      public long totalRuns;
      public long finished;
      public long datasets;

      public RecalculationStatus() {
      }

      public RecalculationStatus(long totalRuns) {
         this.timestamp = System.currentTimeMillis();
         this.totalRuns = totalRuns;
      }
   }
}
