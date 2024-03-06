package io.hyperfoil.tools.horreum.api.internal.services;

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.data.Action;
import io.hyperfoil.tools.horreum.api.data.AllowedSite;
import org.eclipse.microprofile.openapi.annotations.callbacks.Callback;
import org.eclipse.microprofile.openapi.annotations.callbacks.CallbackOperation;
import org.eclipse.microprofile.openapi.annotations.callbacks.Callbacks;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/api/action")
@Consumes({ MediaType.APPLICATION_JSON})
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "action", description = "Manage Actions")
public interface ActionService {
   @POST
   /*@Callback(name = "HttpAction",
   callbackUrlExpression = "http://acme.com",
   operations = {@CallbackOperation (
       method = "GET",
       summary = "A simple Http Action request",
       description="This will initiate a Http callback to a url that is set as an Action.",
       responses = {
           @APIResponse(
              responseCode = "404",
              description = "callback failed",
              content = @Content(
                  mediaType = "text/html"
              )),
           @APIResponse(
               responseCode = "200",
               description = "callback worked",
               content = @Content(
                   mediaType = "text/html"
               )
           )
       }
   )})*/
   @Callbacks(
      value = {
      @Callback(
         name="HttpAction",
         callbackUrlExpression = "http://acme.com",
         operations = {
             @CallbackOperation(
                 method = "GET",
                 summary = "A simple Http Action request",
                 description="This will initiate a Http callback to a url that is set as an Action.",
                 responses = {
                     @APIResponse(
                         responseCode = "404",
                         description = "callback failed",
                         content = @Content(
                             mediaType = "text/html"
                         )),
                     @APIResponse(
                         responseCode = "200",
                         description = "callback worked",
                         content = @Content(
                             mediaType = "text/html"
                         )
                     )
                 }
             )
      }),
      @Callback(
          name="GithubIssueCommentAction",
          callbackUrlExpression = "https://github.com/issue/comment/action",
          operations = {
              @CallbackOperation(
                  method = "POST",
                  summary = "A Http Action request to create a comment",
                  description = "This will initiate a Http callback to a url that is set as an Action.",
                  responses = {
                      @APIResponse(
                          responseCode = "404",
                          description = "callback failed",
                          content = @Content(
                              mediaType = "text/html"
                          )),
                      @APIResponse(
                          responseCode = "200",
                          description = "callback worked",
                          content = @Content(
                              mediaType = "text/html"
                          )
                      )
                  }
              )

       }),
       @Callback(
           name="GithubIssueCreateAction",
           callbackUrlExpression = "https://github.com/issue/create/action",
           operations = {
               @CallbackOperation(
                   method = "POST",
                   summary = "A Http Action request to create an issue",
                   description = "This will initiate a Http callback to a url that is set as an Action.",
                   responses = {
                       @APIResponse(
                           responseCode = "404",
                           description = "callback failed",
                           content = @Content(
                               mediaType = "text/html"
                           )),
                       @APIResponse(
                           responseCode = "200",
                           description = "callback worked",
                           content = @Content(
                               mediaType = "text/html"
                           )
                       )
                   }
               )

           })
      } )
   Action add(Action action);

   @GET
   @Path("{id}")
   Action get(@PathParam("id") int id);

   @DELETE
   @Path("{id}")
   void delete(@PathParam("id") int id);

   @POST
   @Path("update")
   Action update(@RequestBody(required = true) Action action);

   @GET
   @Path("list")
   List<Action> list(@QueryParam("limit") Integer limit,
                     @QueryParam("page") Integer page,
                     @QueryParam("sort") @DefaultValue("id") String sort,
                     @QueryParam("direction") @DefaultValue("Ascending") SortDirection direction);

   @GET
   @Path("test/{id}")
   List<Action> getTestActions(@PathParam("id") int testId);

   @GET
   @Path("allowedSites")
   List<AllowedSite> allowedSites();

   @Consumes("text/plain")
   @POST
   @Path("allowedSites")
   AllowedSite addSite(@RequestBody(required = true) String prefix);

   @DELETE
   @Path("allowedSites/{id}")
   void deleteSite(@PathParam("id") long id);
}
