package io.hyperfoil.tools.horreum.api;

import java.time.Instant;
import java.util.List;

import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.report.ReportComment;
import io.hyperfoil.tools.horreum.entity.report.TableReport;
import io.hyperfoil.tools.horreum.entity.report.TableReportConfig;

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

   class AllTableReports {
      @NotNull
      public List<TableReportSummary> reports;
      @JsonProperty(required = true)
      public long count;
   }

   class TableReportSummary {
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
      @Schema(required = true, type = SchemaType.NUMBER)
      public Instant created;
   }
}
