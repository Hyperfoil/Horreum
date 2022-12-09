package io.hyperfoil.tools.horreum.entity;

import java.time.Instant;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Entity
public class Banner {
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
