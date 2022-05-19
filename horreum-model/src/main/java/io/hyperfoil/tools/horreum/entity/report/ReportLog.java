package io.hyperfoil.tools.horreum.entity.report;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.entity.PersistentLog;

@Entity
public class ReportLog extends PersistentLog {
   @ManyToOne(optional = false)
   @JoinColumn(name = "report_id")
   @JsonIgnore
   TableReport report;

   public ReportLog() {
      super(0, null);
   }

   public ReportLog(TableReport report, int level, String message) {
      super(level, message);
      this.report = report;
   }

   @JsonProperty("reportId")
   public int getReportId() {
      return report.id;
   }

   @JsonProperty("reportId")
   public void setReportId(int reportId) {
      report = TableReport.getEntityManager().getReference(TableReport.class, reportId);
   }
}
