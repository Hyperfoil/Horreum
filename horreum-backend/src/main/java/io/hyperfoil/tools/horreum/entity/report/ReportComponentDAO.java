package io.hyperfoil.tools.horreum.entity.report;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.node.ArrayNode;

@Entity(name = "ReportComponent")
@Table(name = "reportcomponent")
public class ReportComponentDAO {
   @Id
   @GeneratedValue
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "reportconfig_id")
   public TableReportConfigDAO report;

   @NotNull
   public String name;

   @NotNull
   @Column(name = "component_order")
   public int order;

   @NotNull
   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public ArrayNode labels;

   public String function;

   // displayed on Y axis
   public String unit;
}
