package io.hyperfoil.tools.horreum.entity.alerting;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OrderColumn;
import javax.validation.constraints.NotNull;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Represents already created dashboard in Grafana.
 * There should be one dashboard per test.
 */
@Entity(name = "grafana_dashboard")
public class GrafanaDashboard extends PanacheEntityBase {
   @Id
   public String uid;

   @NotNull
   public int testId;

   @NotNull
   public String url;

   @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
   @JoinColumn(name = "grafana_dashboard_uid", referencedColumnName = "uid", nullable = false)
   @OrderColumn(name = "\"order\"")
   public List<GrafanaPanel> panels;
}
