package io.hyperfoil.tools.horreum.entity.data;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;

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
