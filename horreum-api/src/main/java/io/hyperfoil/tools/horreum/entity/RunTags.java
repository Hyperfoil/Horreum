package io.hyperfoil.tools.horreum.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.array.IntArrayType;

@Entity
@Table(name = "run_tags")
@TypeDefs({
      @TypeDef(
            name = "int-array",
            typeClass = IntArrayType.class
      )
})
@Immutable
public class RunTags {
   @Id
   int runId;

   @NotNull
   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   JsonNode tags;

   @NotNull
   @Column(name = "extractor_ids", columnDefinition = "integer[]")
   @Type(type = "int-array")
   int[] extractorIds;
}
