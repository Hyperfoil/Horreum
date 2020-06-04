package io.hyperfoil.tools.repo.entity.json;

import io.hyperfoil.tools.repo.entity.converter.AccessSerializer;
import io.hyperfoil.tools.yaup.json.Json;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.hibernate.annotations.Type;

import javax.json.bind.annotation.JsonbTypeDeserializer;
import javax.json.bind.annotation.JsonbTypeSerializer;
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
      name = "schema",
      uniqueConstraints = @UniqueConstraint(columnNames = {"owner", "uri"})
)
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
   public String uri;

   @NotNull
   @Column(name="name",unique = true)
   public String name;

   public String description;

   @Type(type = "io.hyperfoil.tools.repo.entity.converter.JsonUserType")
   public Json schema;

   /**
    * JsonPath query selecting the test name.
    */
   public String testPath;

   /**
    * JsonPath query selecting start timestamp.
    */
   public String startPath;

   /**
    * JsonPath query selection stop timestamp;
    */
   public String stopPath;

   @NotNull
   public String owner;

   public String token;

   @NotNull
   @JsonbTypeSerializer(AccessSerializer.class)
   @JsonbTypeDeserializer(AccessSerializer.class)
   public Access access;
}
