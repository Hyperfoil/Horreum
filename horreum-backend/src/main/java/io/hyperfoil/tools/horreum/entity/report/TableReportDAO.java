package io.hyperfoil.tools.horreum.entity.report;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;

import static jakarta.persistence.GenerationType.SEQUENCE;

@Entity(name = "TableReport")
@Table(name = "tablereport")
public class TableReportDAO extends PanacheEntityBase {
   @Id
   @SequenceGenerator(
         name = "tableReportIdGenerator",
         sequenceName = "tablereport_seq"
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "tableReportIdGenerator")
   public Integer id;

   @OneToOne(fetch = FetchType.EAGER)
   @JoinColumn(name = "config_id")
   public TableReportConfigDAO config;

   @NotNull
   public Instant created;

   @NotNull
   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, mappedBy = "report")
   @Fetch(FetchMode.SELECT)
   public Collection<ReportCommentDAO> comments;

   @NotNull
   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "tablereport_data", joinColumns = @JoinColumn(name = "report_id"))
   @Fetch(FetchMode.SELECT)
   public Collection<Data> data;

   @NotNull
   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, mappedBy = "report")
   @Fetch(FetchMode.SELECT)
   public Collection<ReportLogDAO> logs = new ArrayList<>();

   @Embeddable
   public static class Data {
      @NotNull
      @Column(name = "dataset_id")
      public int datasetId;

      @NotNull
      public int runId;

      @NotNull
      public int ordinal;

      @NotNull
      public String category;
      @NotNull
      public String series;
      @NotNull
      public String scale;

      @NotNull
      @Type(JsonBinaryType.class)
      @Column(columnDefinition = "jsonb")
      public ArrayNode values;

      @Override
      public String toString() {
         return "TableReport.Data{" +
               "datasetId=" + datasetId +
               ", category='" + category + '\'' +
               ", series='" + series + '\'' +
               ", label='" + scale + '\'' +
               ", values=" + values +
               '}';
      }
   }
}
