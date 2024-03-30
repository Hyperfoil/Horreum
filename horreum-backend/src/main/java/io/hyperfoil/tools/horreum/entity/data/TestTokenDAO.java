package io.hyperfoil.tools.horreum.entity.data;

import java.util.function.Function;

import io.hyperfoil.tools.horreum.entity.SeqIdGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;

import static jakarta.persistence.GenerationType.SEQUENCE;
import static org.hibernate.id.OptimizableGenerator.INCREMENT_PARAM;

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
         type = SeqIdGenerator.class,
         parameters = { @Parameter(name = INCREMENT_PARAM, value = "1") }
   )
   @GeneratedValue(strategy = SEQUENCE, generator = "tokenIdGenerator")
   public Integer id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "test_id")
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

   public void setValue(String value) {
      this.value = value;
   }

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
