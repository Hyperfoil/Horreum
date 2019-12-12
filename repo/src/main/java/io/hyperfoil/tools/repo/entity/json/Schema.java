package io.hyperfoil.tools.repo.entity.json;

import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.validation.constraints.NotNull;

@Entity
@RegisterForReflection
public class Schema extends PanacheEntityBase {

   @Id
   @SequenceGenerator(
      name = "schemaSequence",
      sequenceName = "schema_id_seq",
      allocationSize = 1,
      initialValue = 1)
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "schemaSequence")
   public Integer id;

   @NotNull
   @Column(name="name",unique = true)
   public String name;

   public String description;

   @Type(type = "io.hyperfoil.tools.repo.entity.converter.JsonUserType")
   public Json schema;


}
