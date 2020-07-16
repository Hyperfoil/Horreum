package io.hyperfoil.tools.horreum.entity.json;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

@Entity
@RegisterForReflection
@Table(
   name = "hook",
   uniqueConstraints = {
      @UniqueConstraint(
         columnNames = {"url","type","target"}
      )
   }
)
public class Hook extends PanacheEntityBase {

   @Id
   @SequenceGenerator(
      name = "hookSequence",
      sequenceName = "hook_id_seq",
      allocationSize = 1,
      initialValue = 1)
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hookSequence")
   public Integer id;

   @NotNull
   @Column(name = "url")
   public String url;

   @NotNull
   @Column(name = "type")
   public String type;

   @NotNull
   @Column(name = "target")
   public Integer target;

   @NotNull
   public boolean active;
}
