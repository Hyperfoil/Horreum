package io.hyperfoil.tools.repo.api;

import io.hyperfoil.tools.yaup.AsciiArt;
import io.hyperfoil.tools.yaup.json.Json;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/api/log")
@Consumes({MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
public class LogService {

   @GET
   @Path("/pattern/{pattern:.*}")
   public String echoPattern(@PathParam("pattern") String pattern){
      return pattern;
   }

   @GET
   public String logGet(){
      System.out.println(AsciiArt.ANSI_RED+"GET /api/log"+AsciiArt.ANSI_RESET);return "ok";
   }

   @POST
   public Response logPost(Json json){
      System.out.println(AsciiArt.ANSI_RED+"POST /api/log\n"+AsciiArt.ANSI_RESET+json);
      System.out.println("going to sleep");
      try {
         Thread.sleep(10_000);
      } catch (InterruptedException e) {
         e.printStackTrace();
      }
      System.out.println("I'm awake!");
      return Response.ok().build();
   }

   @GET
   @Path("status/{status}")
   public Response getStatus(@PathParam("status")Integer status){
      return Response.status(status).entity("entity").build();
   }
}
