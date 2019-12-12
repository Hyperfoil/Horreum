package io.hyperfoil.tools.repo.api;

import io.hyperfoil.tools.repo.entity.json.Schema;
import io.hyperfoil.tools.repo.entity.json.Test;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.util.List;

@Path("api/schema")
public class SchemaService {

   @Inject
   EntityManager em;

   @GET
   @Path("{name:.*}")
   public Schema getSchema(@PathParam("name")String name){
      Schema rtrn = Schema.find("name",name).firstResult();
      return rtrn;
   }

   @POST
   public Response add(Schema schema){
      Schema byName = Schema.find("name",schema.name).firstResult();
      if(byName!=null){
         if(schema.id == byName.id){
            em.merge(schema);
         } else {
            Json response = new Json();
            Response.serverError().entity("Name already used");
         }
      }else{
         schema.id = null; //remove the id so we don't override an existing entry
         em.persist(schema);
      }
      em.flush();//manually flush to validate constraints
      return Response.ok(schema.id).build();
   }

   public List<Schema> all(){
      return list(null,null,"name","Ascending");
   }

   @GET
   public List<Schema> list(@QueryParam("limit") Integer limit, @QueryParam("page") Integer page, @QueryParam("sort") String sort, @QueryParam("direction") String direction){
      if(sort == null || sort.isEmpty()){
         sort = "name";
      }
      if(direction == null || direction.isEmpty()){
         direction = "Ascending";
      }
      if(limit != null && page != null){
         return Schema.findAll(Sort.by(sort).direction(Sort.Direction.valueOf(direction))).page(Page.of(page,limit)).list();
      }else{
         return Schema.listAll(Sort.by(sort).direction(Sort.Direction.valueOf(direction)));
      }
   }

//I'm not sure being able to delete a schema is a good idea since we don't have reference tracking built into the table
//   @DELETE
//   @Path("{name:.*}")
//   public Response delete(@PathParam("name")String name){
//      Schema byName = Schema.find("name",name).firstResult();
//      if(byName == null){
//         return Response.noContent().build();
//      }else{
//         byName.delete();
//         return Response.ok(byName.id).build();
//      }
//   }
}
