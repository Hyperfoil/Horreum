package io.hyperfoil.tools.horreum.entity.report;

import javax.json.bind.annotation.JsonbTransient;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "reportcomponent")
public class ReportComponent {
   @Id
   @GeneratedValue
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JsonbTransient
   @JsonIgnore
   @JoinColumn(name = "reportconfig_id")
   public TableReportConfig report;

   @NotNull
   public String name;

   @NotNull
   @Column(name = "component_order")
   public int order;

   @NotNull
   public String accessors;
   public String function;
}
