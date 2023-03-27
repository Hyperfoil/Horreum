package io.hyperfoil.tools.horreum.entity.report;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.PersistentLog;

@Entity(name = "ReportLog")
public class ReportLogDAO extends PersistentLog {
   @ManyToOne(optional = false)
   @JoinColumn(name = "report_id")
   @JsonIgnore
   TableReportDAO report;

   public ReportLogDAO() {
      super(0, null);
   }

   public ReportLogDAO(TableReportDAO report, int level, String message) {
      super(level, message);
      this.report = report;
   }

   @JsonProperty(required = true, value = "reportId")
   public int getReportId() {
      if (report == null || report.id == null) {
         return -1;
      }
      return report.id;
   }

   @JsonProperty("reportId")
   public void setReportId(int reportId) {
      report = getEntityManager().getReference(TableReportDAO.class, reportId);
   }
}
