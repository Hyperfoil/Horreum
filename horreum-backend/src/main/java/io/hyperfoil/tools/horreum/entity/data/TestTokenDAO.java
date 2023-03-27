package io.hyperfoil.tools.horreum.entity.data;

import java.util.function.Function;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

@Entity(name = "test_token")
public class TestTokenDAO {
   public static final int READ = 1;
   // e.g. change config, or delete
   public static final int MODIFY = 2;
   // e.g. for test this grants upload of runs
   public static final int UPLOAD = 4;

   @Id
   @NotNull
   @GenericGenerator(
         name = "tokenIdGenerator",
         strategy = "io.hyperfoil.tools.horreum.entity.SeqIdGenerator",
         parameters = {
               @Parameter(name = SequenceStyleGenerator.SEQUENCE_PARAM, value = SequenceStyleGenerator.DEF_SEQUENCE_NAME),
               @Parameter(name = SequenceStyleGenerator.INCREMENT_PARAM, value = "1"),
         }
   )
   @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "tokenIdGenerator")
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "test_id")
   @JsonIgnore
   public TestDAO test;

   @SuppressWarnings({ "unused", "FieldCanBeLocal" })
   @NotNull
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

   public boolean valueEquals(String value) {
      return this.value.equals(value);
   }

   public String getEncryptedValue(Function<String, String> encrypt) {
      return encrypt.apply(value);
   }

   public void decryptValue(Function<String, String> decrypt) {
      this.value = decrypt.apply(value);
   }
}
