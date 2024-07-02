package io.hyperfoil.tools.horreum.entity.report;

import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;

import static jakarta.persistence.GenerationType.SEQUENCE;

@Entity(name = "ReportLog")
public class ReportLogDAO extends PersistentLogDAO {
   @Id
   @SequenceGenerator(
         name = "reportlog_id_generator",
         sequenceName = "reportlog_id_generator",
         allocationSize = 1
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "reportlog_id_generator")
   public Long id;
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
