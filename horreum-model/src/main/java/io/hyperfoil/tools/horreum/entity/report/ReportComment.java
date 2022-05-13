package io.hyperfoil.tools.horreum.entity.report;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
public class ReportComment extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @JsonIgnore
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "report_id", nullable = false)
   public TableReport report;

   // 0 = root comment, 1 = on category, 2 = on component
   @NotNull
   public int level;

   public String category;

   @Column(name = "component_id")
   public int componentId;

   @NotNull
   public String comment;
}
