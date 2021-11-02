package io.hyperfoil.tools.horreum.entity.report;

import java.time.Instant;
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
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import com.vladmihalcea.hibernate.type.array.DoubleArrayType;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "tablereport")
public class TableReport extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "config_id")
   public TableReportConfig config;

   @NotNull
   public Instant created;

   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
   @JoinColumn(name = "report_id")
   @Fetch(FetchMode.SELECT)
   public Collection<ReportComment> comments;

   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "tablereport_rundata", joinColumns = @JoinColumn(name = "report_id"))
   @Fetch(FetchMode.SELECT)
   public Collection<RunData> runData;

   @Embeddable
   @TypeDefs(@TypeDef(name = "double-array", typeClass = DoubleArrayType.class))
   public static class RunData {
      @NotNull
      @Column(name = "runid")
      public int runId;
      @NotNull
      public String category;
      @NotNull
      public String series;
      @NotNull
      public String label;

      @Type(type = "double-array")
      @Column(name = "values", columnDefinition = "float8[]")
      @NotNull
      public double[] values;
   }
}
