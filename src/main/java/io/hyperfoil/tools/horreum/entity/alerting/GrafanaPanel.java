package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OrderColumn;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "grafana_panel")
public class GrafanaPanel extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   public String name;

   @ManyToMany(fetch = FetchType.EAGER)
   @OrderColumn
   public List<Variable> variables;
}
