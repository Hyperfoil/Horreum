package io.hyperfoil.tools.horreum.entity.report;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Entity
@Table(name = "reportcomponent")
public class ReportComponent {
   @Id
   @GeneratedValue
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JsonIgnore
   @JoinColumn(name = "reportconfig_id")
   public TableReportConfig report;

   @NotNull
   public String name;

   @NotNull
   @Column(name = "component_order")
   public int order;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public ArrayNode labels;

   public String function;

   // displayed on Y axis
   public String unit;
}
