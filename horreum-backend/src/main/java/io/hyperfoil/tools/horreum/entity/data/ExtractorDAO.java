package io.hyperfoil.tools.horreum.entity.data;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotNull;

@Embeddable
public class ExtractorDAO {
   @NotNull
   public String name;

   @NotNull
   public String jsonpath;

   @NotNull
   @Column(name = "isarray")
   public boolean array;

   public ExtractorDAO() {
   }

   public ExtractorDAO(String name, String jsonpath, boolean array) {
      this.name = name;
      this.jsonpath = jsonpath;
      this.array = array;
   }
}
