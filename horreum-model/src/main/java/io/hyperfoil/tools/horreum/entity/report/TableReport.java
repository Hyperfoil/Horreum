package io.hyperfoil.tools.horreum.entity.report;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;

import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "tablereport")
public class TableReport extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @OneToOne(fetch = FetchType.EAGER)
   @JoinColumn(name = "config_id")
   public TableReportConfig config;

   @NotNull
   public Instant created;

   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, mappedBy = "report")
   @Fetch(FetchMode.SELECT)
   public Collection<ReportComment> comments;

   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "tablereport_data", joinColumns = @JoinColumn(name = "report_id"))
   @Fetch(FetchMode.SELECT)
   public Collection<Data> data;

   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER, mappedBy = "report")
   @Fetch(FetchMode.SELECT)
   public Collection<ReportLog> logs = new ArrayList<>();

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

      @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
      @NotNull
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
