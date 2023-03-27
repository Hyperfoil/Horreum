package io.hyperfoil.tools.horreum.entity.data;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.validation.constraints.NotNull;

@Embeddable
public class Extractor {
   @NotNull
   public String name;

   @NotNull
   public String jsonpath;

   @NotNull
   @Column(name = "isarray")
   public boolean array;

   public Extractor() {
   }

   public Extractor(String name, String jsonpath, boolean array) {
      this.name = name;
      this.jsonpath = jsonpath;
      this.array = array;
   }
}
