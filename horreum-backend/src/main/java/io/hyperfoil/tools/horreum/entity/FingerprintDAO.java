package io.hyperfoil.tools.horreum.entity;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.data.DatasetDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.Type;

@Entity(name = "Fingerprint")
public class FingerprintDAO extends PanacheEntityBase {
   @Id
   @Column(name = "dataset_id")
   public Integer datasetId;

   @OneToOne(fetch = FetchType.LAZY)
   @MapsId
   @JoinColumn(name = "dataset_id")
   public DatasetDAO dataset;

   @Type(JsonBinaryType.class)
   @Column(columnDefinition = "jsonb")
   public JsonNode fingerprint;

   public Integer fp_hash;

   @Override
   public String toString() {
      return "FP{" +
              "datasetId=" + datasetId +
              ", fingerprint=" + fingerprint +
              ", fp_hash=" + fp_hash +
              '}';
   }
}
