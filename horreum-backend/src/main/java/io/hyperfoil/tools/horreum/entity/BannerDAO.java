package io.hyperfoil.tools.horreum.entity;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;

@Entity(name = "Banner")
public class BannerDAO {
   @Id
   @GeneratedValue
   public Integer id;

   public Instant created;

   @NotNull
   public boolean active;

   @NotNull
   public String severity;

   @NotNull
   public String title;

   public String message;
}
