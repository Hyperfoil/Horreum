package io.hyperfoil.tools.horreum.entity.report;

import jakarta.persistence.*;

import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

@Entity(name = "ReportLog")
public class ReportLogDAO extends PersistentLogDAO {
   @Id
   @GenericGenerator(
           name = "reportlog_id_generator",
           strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
           parameters = {
                   @org.hibernate.annotations.Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
           }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "reportlog_id_generator")
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
