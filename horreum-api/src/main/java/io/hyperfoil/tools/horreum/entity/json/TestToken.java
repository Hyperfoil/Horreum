package io.hyperfoil.tools.horreum.entity.json;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

@Entity(name = "test_token")
public class TestToken {
   public static final int READ = 1;
   // e.g. change config, or delete
   public static final int MODIFY = 2;
   // e.g. for test this grants upload of runs
   public static final int UPLOAD = 4;

   @Id
   @GeneratedValue
   @NotNull
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "test_id")
   @JsonIgnore
   public Test test;

   @SuppressWarnings({ "unused", "FieldCanBeLocal" })
   @NotNull
   @JsonIgnore
   @Column()
   private String value;

   // e.g. read, upload (run), modify...
   @NotNull
   public int permissions;

   @NotNull
   public String description;

   @JsonSetter("value")
   public void setValue(String value) {
      this.value = value;
   }

   @JsonGetter("value")
   public String getValue() {
      return null;
   }

   public boolean hasRead() {
      return (permissions & READ) != 0;
   }

   public boolean hasModify() {
      return (permissions & MODIFY) != 0;
   }

   public boolean hasUpload() {
      return (permissions & UPLOAD) != 0;
   }
}
