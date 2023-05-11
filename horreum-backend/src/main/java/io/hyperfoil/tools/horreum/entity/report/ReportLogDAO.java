package io.hyperfoil.tools.horreum.entity.report;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import io.hyperfoil.tools.horreum.entity.PersistentLog;

@Entity(name = "ReportLog")
public class ReportLogDAO extends PersistentLog {
   @ManyToOne(optional = false)
   @JoinColumn(name = "report_id")
   TableReportDAO report;

   public ReportLogDAO() {
      super(0, null);
   }

   public ReportLogDAO(TableReportDAO report, int level, String message) {
      super(level, message);
      this.report = report;
   }

   public int getReportId() {
      if (report == null || report.id == null) {
         return -1;
      }
      return report.id;
   }

   public void setReportId(int reportId) {
      report = getEntityManager().getReference(TableReportDAO.class, reportId);
   }
}
