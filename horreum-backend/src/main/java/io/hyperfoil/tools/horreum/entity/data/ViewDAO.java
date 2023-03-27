package io.hyperfoil.tools.horreum.entity.data;

import java.util.Collections;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.hyperfoil.tools.horreum.api.ApiIgnore;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/**
 * Security model: the access to view is limited by access to the referenced test.
 */
@Entity(name = "view")
@Table(uniqueConstraints = @UniqueConstraint(columnNames = { "id", "name" }))
public class ViewDAO extends PanacheEntityBase {
   @JsonProperty(required = true)
   @Id
   @GenericGenerator(
         name = "viewIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = SequenceStyleGenerator.DEF_SEQUENCE_NAME),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "viewIdGenerator")
   public Integer id;

   @NotNull
   public String name;

   // In the future we could make this ManyToMany, but then we'd have to maintain
   // ownership and access in this entity separately.
   @ManyToOne(fetch = FetchType.LAZY)
   @JsonIgnore
   public TestDAO test;

   @NotNull
   @OneToMany(fetch = FetchType.EAGER, mappedBy = "view", orphanRemoval = true, cascade = CascadeType.ALL)
   @OrderBy("headerorder ASC")
   public List<ViewComponent> components;

   public ViewDAO() {
   }

   public ViewDAO(String name, TestDAO test) {
      this.name = name;
      this.test = test;
      this.components = Collections.emptyList();
   }

   @ApiIgnore
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
}
