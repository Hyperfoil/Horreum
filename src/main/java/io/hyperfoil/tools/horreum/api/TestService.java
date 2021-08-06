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

import io.hyperfoil.tools.horreum.entity.json.Access;
import io.hyperfoil.tools.horreum.entity.json.Hook;
import io.hyperfoil.tools.horreum.entity.json.Test;
import io.hyperfoil.tools.horreum.entity.json.TestToken;
import io.hyperfoil.tools.horreum.entity.json.View;
import io.hyperfoil.tools.yaup.json.Json;
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

   Test getByNameOrId(String input);

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
   List<TestSummary> summary(@QueryParam("roles") String roles);

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
   @Path("{testId}/hook")
   void updateHook(@PathParam("testId") Integer testId, Hook hook);

   @GET
   @Path("{id}/tags")
   List<Json> tags(@PathParam("id") Integer testId, @QueryParam("trashed") Boolean trashed);

   class TestSummary {
      public int id;
      public String name;
      public String description;
      public Number count; // SQL count(*) returns BigInteger
      public String owner;
      public int access;
   }
}
