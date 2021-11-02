package io.hyperfoil.tools.horreum.entity.json;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.annotations.Type;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.databind.JsonNode;

@Entity
@RegisterForReflection
@Table(
      name = "schema",
      uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "uri"})
)
public class Schema extends ProtectedBaseEntity {

   @Id
   @SequenceGenerator(
      name = "schemaSequence",
      sequenceName = "schema_id_seq",
      allocationSize = 1)
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "schemaSequence")
   public Integer id;

   @NotNull
   public String uri;

   @NotNull
   @Column(name="name",unique = true)
   public String name;

   public String description;

   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode schema;
}
