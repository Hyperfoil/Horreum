package io.hyperfoil.tools.horreum.entity.json;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Security model: the access to view is limited by access to the referenced test.
 */
@Entity(name = "view")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "id", "name" }))
public class View extends PanacheEntityBase {
   @Id
   @GeneratedValue
   public Integer id;

   @NotNull
   public String name;

   // In the future we could make this ManyToMany, but then we'd have to maintain
   // ownership and access in this entity separately.
   @ManyToOne(fetch = FetchType.LAZY)
   @JsonIgnore
   public Test test;

   @OneToMany(mappedBy = "view", orphanRemoval = true, cascade = CascadeType.ALL)
   @OrderBy("headerorder ASC")
   public List<ViewComponent> components;

   public void ensureLinked() {
      if (components != null) {
         for (ViewComponent c : components) {
            if (c.id != null && c.id < 0) {
               c.id = null;
            }
            c.view = this;
         }
      }
   }

   public void copyIds(View other) {
      this.id = other.id;
      if (components != null && other.components != null) {
         for (ViewComponent c : components) {
            other.components.stream()
                  .filter(o -> o.headerName.equals(c.headerName))
                  .findFirst().ifPresent(o -> {
                     c.id = o.id;
                     c.view = o.view;
                  });
         }
      }
   }
}
