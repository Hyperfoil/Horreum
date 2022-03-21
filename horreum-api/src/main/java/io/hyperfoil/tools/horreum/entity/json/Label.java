package io.hyperfoil.tools.horreum.entity.json;

import java.util.Collection;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.SequenceGenerator;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.runtime.annotations.RegisterForReflection;

@Entity(name="label")
@RegisterForReflection
public class Label extends PanacheEntityBase {

   public static final String LABEL_NEW = "label/new";

   @Id
   @SequenceGenerator(
         name="labelSequence",
         sequenceName="label_id_seq",
         allocationSize=1)
   @GeneratedValue(strategy=GenerationType.SEQUENCE,
         generator= "labelSequence")
   public Integer id;

   @NotNull
   public String label;

   @NotNull
   public String value;

   @NotNull
   @JoinColumn (name = "datasetid")
   @JsonIgnore
   public DataSet dataSet;

   @ElementCollection(fetch = FetchType.EAGER)
   @CollectionTable(name = "label_functions")
   public Collection<ConfiguredJsonPath> jsonPaths;

   @NotNull
   public Boolean filtering;

   @NotNull
   public Boolean metrics;
}
