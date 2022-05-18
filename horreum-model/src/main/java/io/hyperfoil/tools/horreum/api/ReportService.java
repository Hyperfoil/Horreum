package io.hyperfoil.tools.horreum.api;

import java.time.Instant;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

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
   TableReportConfig getTableReportConfig(@PathParam("id") Integer id);

   @POST
   @Path("table/preview")
   TableReport previewTableReport(TableReportConfig config, @QueryParam("edit") Integer updatedReportId);

   @POST
   @Path("table/config")
   TableReport updateTableReportConfig(TableReportConfig config, @QueryParam("edit") Integer updatedReportId);

   @GET
   @Path("table/{id}")
   TableReport getTableReport(@PathParam("id") Integer id);

   @DELETE
   @Path("table/{id}")
   void deleteTableReport(@PathParam("id") Integer id);

   @POST
   @Path("comment/{reportId}")
   ReportComment updateComment(@PathParam("reportId") Integer reportId, ReportComment comment);

   class AllTableReports {
      public List<TableReportSummary> reports;
      public long count;
   }

   class TableReportSummary {
      public int testId;
      public String testName;
      public String title;
      public List<TableReportSummaryItem> reports;
   }

   class TableReportSummaryItem {
      public int id;
      public int configId;
      public Instant created;
   }
}
