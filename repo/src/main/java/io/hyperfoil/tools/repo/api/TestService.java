package io.hyperfoil.tools.repo.api;

import io.hyperfoil.tools.repo.entity.json.Test;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.web.common.template.test;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("/api/test")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class TestService {

   @Inject
   EntityManager em;

   @Inject
   EventBus eventBus;


   @DELETE
   @Path("{id}")
   public void delete(@PathParam("id") Integer id){
      Test.find("id",id).firstResult().delete();
   }

   @GET
   @Path("{id}")
   public Test get(@PathParam("id") Integer id){
      return Test.find("id",id).firstResult();
   }

   public Test getByNameOrId(String input){
      Test foundTest = getByName(input);
      if(foundTest == null && input.matches("-?\\d+")){
         foundTest = get(Integer.parseInt(input));
      }
      return foundTest;
   }

   public Test getByName(String name){
      Test existing = Test.find("name", name).firstResult();
      return existing;
   }

   @GET
   @Path("{id}/schema")
   public Response getSchema(@PathParam("id") Integer id){
      Test t =  Test.find("id",id).firstResult();
      if(t!=null){
         return Response.ok(t.schema).build();
      }else{
         return Response.noContent().build();
      }
   }
   @POST
   @Path("{id}/schema")
   @Transactional
   public Response setSchema(@PathParam("id") Integer id, Json schema){
      Test t =  Test.find("id",id).firstResult();
      if( t != null){
         t.schema = schema;
         em.persist(t);
         return Response.ok().build();
      }else{
         return Response.noContent().build();
      }

   }

   @POST
   @Transactional
   public Response add(Test test){
      System.out.println("add TEST");
      if(test == null){
         return Response.serverError().entity("test is null").build();
      }
      Test existing = Test.find("name",test.name).firstResult();
      if(existing!=null){
         test.id = existing.id;
         em.merge(test);
      }else{
         em.persist(test);
         eventBus.publish("new/test",test);

      }
      //test.persistAndFlush();
      return Response.ok(test).build();
   }

   public List<Test> all(){
      return list(null,null,"name","Ascending");
   }

   @GET
   public List<Test> list(@QueryParam("limit") Integer limit, @QueryParam("page") Integer page, @QueryParam("sort") String sort,@QueryParam("direction") String direction){
      if(sort == null || sort.isEmpty()){
         sort = "name";
      }
      if(direction == null || direction.isEmpty()){
         direction = "Ascending";
      }
      if(limit != null && page != null){
         return Test.findAll(Sort.by(sort).direction(Sort.Direction.valueOf(direction))).page(Page.of(page,limit)).list();
      }else{
         return Test.listAll(Sort.by(sort).direction(Sort.Direction.valueOf(direction)));
      }
   }
}
