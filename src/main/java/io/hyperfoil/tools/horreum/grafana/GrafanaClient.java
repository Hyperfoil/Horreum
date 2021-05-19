package io.hyperfoil.tools.horreum.grafana;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "horreum.grafana")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface GrafanaClient {

   @POST
   @Path("/api/dashboards/db")
   @ClientHeaderParam(name = HttpHeaders.AUTHORIZATION, value = "{authorizationToken}")
   DashboardSummary createOrUpdateDashboard(PostDashboardRequest request);

   @GET
   @Path("/api/dashboards/uid/{uid}")
   @ClientHeaderParam(name = HttpHeaders.AUTHORIZATION, value = "{authorizationToken}")
   GetDashboardResponse getDashboard(@PathParam("uid") String uid);

   @GET
   @Path("/api/search")
   @ClientHeaderParam(name = HttpHeaders.AUTHORIZATION, value = "{authorizationToken}")
   List<DashboardSummary> searchDashboard(@QueryParam("query") String name);

   @DELETE
   @Path("/api/dashboards/uid/{uid}")
   @ClientHeaderParam(name = HttpHeaders.AUTHORIZATION, value = "{authorizationToken}")
   void deleteDashboard(@PathParam("uid") String uid);

   @GET
   @Path("/api/users/lookup")
   @ClientHeaderParam(name = HttpHeaders.AUTHORIZATION, value = "{authorizationBasic}")
   UserInfo lookupUser(@QueryParam("loginOrEmail") String email);

   @POST
   @Path("/api/admin/users")
   @ClientHeaderParam(name = HttpHeaders.AUTHORIZATION, value = "{authorizationBasic}")
   void createUser(UserInfo user);

   default String authorizationToken() {
      return "Bearer " + ConfigProvider.getConfig().getValue("horreum.grafana.api.key", String.class);
   }

   // Grafana does not support authorization for user management, so we need to fall back to basic auth
   default String authorizationBasic() {
      String adminUser = ConfigProvider.getConfig().getValue("horreum.grafana.admin.user", String.class);
      String adminPassword = ConfigProvider.getConfig().getValue("horreum.grafana.admin.password", String.class);
      return "Basic " + Base64.getEncoder().encodeToString((adminUser + ":" + adminPassword).getBytes(StandardCharsets.UTF_8));
   }

   class UserSearch {
      public int totalCount;
      public List<UserInfo> users;
      public int page;
      public int perPage;
   }

   class UserInfo {
      public Integer id;
      public String email;
      public String name;
      public String login;
      public String password;
      public int orgId;

      public UserInfo() {}

      public UserInfo(String email, String name, String login, String password, int orgId) {
         this.email = email;
         this.name = name;
         this.login = login;
         this.password = password;
         this.orgId = orgId;
      }
   }

   class PostDashboardRequest {
      public Dashboard dashboard;
      public boolean overwrite;

      public PostDashboardRequest(Dashboard dashboard, boolean overwrite) {
         this.dashboard = dashboard;
         this.overwrite = overwrite;
      }
   }

   class DashboardSummary {
      public int id;
      public String slug;
      public String status;
      public String uid;
      public String url;
      public int version;
   }

   class GetDashboardResponse {
      public Dashboard dashboard;
   }

}
