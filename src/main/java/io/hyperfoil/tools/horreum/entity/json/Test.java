package io.hyperfoil.tools.horreum.entity.json;

import io.hyperfoil.tools.horreum.entity.converter.AccessSerializer;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.json.bind.annotation.JsonbTypeDeserializer;
import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity(name="test")
@RegisterForReflection
public class Test extends PanacheEntityBase {
   public static final String EVENT_NEW = "test/new";

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

   public String tags;

   @OneToOne(cascade = { CascadeType.REMOVE, CascadeType.MERGE })
   public View defaultView;

   @NotNull
   public String owner;

   public String token;

   @NotNull
   @JsonbTypeSerializer(AccessSerializer.class)
   @JsonbTypeDeserializer(AccessSerializer.class)
   public Access access = Access.PUBLIC;

   public String compareUrl;

   public void ensureLinked() {
      if (defaultView != null) {
         defaultView.test = this;
         defaultView.ensureLinked();
      }
   }

   public void copyIds(Test other) {
      this.id = other.id;
      if (defaultView != null && other.defaultView != null) {
         defaultView.copyIds(other.defaultView);
      }
   }
}