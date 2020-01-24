package io.hyperfoil.tools.repo.entity.json;

import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity(name="test")
@RegisterForReflection
public class Test extends PanacheEntityBase {

   @Id
   @SequenceGenerator(
      name = "testSequence",
      sequenceName = "test_id_seq",
      allocationSize = 1,
      initialValue = 10) // skip 10 to account for import.sql entries
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "testSequence")
   @Column(name="id")
   public Integer id;

   @NotNull
   @Column(name="name",unique = true)
   public String name;

   @Column(name="description",unique = false)
   public String description;

   @Type(type = "io.hyperfoil.tools.repo.entity.converter.JsonUserType")
   public Json schema;

   @Type(type = "io.hyperfoil.tools.repo.entity.converter.JsonUserType")
   public Json view;

   @NotNull
   public String owner;

   public String token;

   @NotNull
   public Access access = Access.PUBLIC;
}