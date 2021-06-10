package io.hyperfoil.tools.horreum.entity.json;

import java.util.Collection;

import io.hyperfoil.tools.horreum.entity.converter.AccessSerializer;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

import javax.json.bind.annotation.JsonbTypeDeserializer;
import javax.json.bind.annotation.JsonbTypeSerializer;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
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

   @NotNull
   public String owner;

   @NotNull
   @JsonbTypeSerializer(AccessSerializer.class)
   @JsonbTypeDeserializer(AccessSerializer.class)
   public Access access = Access.PUBLIC;

   @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
   public Collection<TestToken> tokens;

   public String tags;

   @OneToOne(cascade = { CascadeType.REMOVE, CascadeType.MERGE }, orphanRemoval = true)
   public View defaultView;

   public String compareUrl;

   @ElementCollection
   @CollectionTable(name = "test_stalenesssettings")
   public Collection<StalenessSettings> stalenessSettings;

   @NotNull
   @Column(columnDefinition = "boolean default true")
   public Boolean notificationsEnabled;

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