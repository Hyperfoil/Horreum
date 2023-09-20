package io.hyperfoil.tools.horreum.api.services;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.hyperfoil.tools.horreum.api.SortDirection;
import io.hyperfoil.tools.horreum.api.report.ReportComment;
import io.hyperfoil.tools.horreum.api.report.TableReportConfig;
import io.hyperfoil.tools.horreum.api.report.TableReport;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@Path("/api/report")
@Produces(MediaType.APPLICATION_JSON)
@Consumes({ MediaType.APPLICATION_JSON})
public interface ReportService {
   @GET
   @Path("table")
   AllTableReports getTableReports(
         @QueryParam("folder") String folder,
         @QueryParam("test") Integer testId,
         @QueryParam("roles") String roles,
         @QueryParam("limit") Integer limit,
         @QueryParam("page") Integer page,
         @QueryParam("sort") String sort,
         @QueryParam("direction") SortDirection direction);

   @GET
   @Path("table/config/{id}")
   TableReportConfig getTableReportConfig(@PathParam("id") int id);

   @POST
   @Path("table/preview")
   TableReport previewTableReport(TableReportConfig config, @QueryParam("edit") Integer updatedReportId);

   @POST
   @Path("table/config")
   TableReport updateTableReportConfig(TableReportConfig config, @QueryParam("edit") Integer updatedReportId);

   @GET
   @Path("table/{id}")
   TableReport getTableReport(@PathParam("id") int id);

   @DELETE
   @Path("table/{id}")
   void deleteTableReport(@PathParam("id") int id);

   @POST
   @Path("comment/{reportId}")
   ReportComment updateComment(@PathParam("reportId") int reportId, ReportComment comment);

   @GET
   @Path("table/config/{id}/export")
   JsonNode exportTableReportConfig(@PathParam("id") int id);

   @POST
   @Path("table/config/import")
   void importTableReportConfig(JsonNode config);
   
   class AllTableReports {
      @NotNull
      public List<TableReportSummary> reports;
      @JsonProperty(required = true)
      public long count;
   }

   class TableReportSummary {
      @JsonProperty(required = true)
      public int configId;
      @JsonProperty(required = true)
      public int testId;
      @NotNull
      public String testName;
      @NotNull
      public String title;
      @NotNull
      public List<TableReportSummaryItem> reports;
   }

   class TableReportSummaryItem {
      @JsonProperty(required = true)
      public int id;
      @JsonProperty(required = true)
      public int configId;
      @JsonProperty(required = true)
      public Instant created;
   }
}
