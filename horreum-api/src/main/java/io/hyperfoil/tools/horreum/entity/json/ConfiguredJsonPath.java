package io.hyperfoil.tools.horreum.entity.json;

import javax.persistence.Embeddable;
import javax.persistence.Embedded;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.validation.constraints.NotNull;

@Embeddable
public class ConfiguredJsonPath{

   @Id
   @SequenceGenerator(
      name = "labelFunctionsSequence",
      sequenceName="label_functions_id_seq",
      allocationSize=1)
   @GeneratedValue(strategy=GenerationType.SEQUENCE,
   generator = "label_functions_id_seq")
   public Integer id;

   @NotNull
   public Boolean is_array;

   @Embedded
   public NamedJsonPath path;
}
