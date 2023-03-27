package io.hyperfoil.tools.horreum.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.MapsId;
import javax.persistence.OneToOne;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.data.DataSetDAO;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "Fingerprint")
@Immutable
public class FingerprintDAO extends PanacheEntityBase {
   @Id
   @Column(name = "dataset_id")
   public int datasetId;

   @OneToOne(fetch = FetchType.LAZY)
   @MapsId
   @JoinColumn(name = "dataset_id")
   public DataSetDAO dataset;

   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode fingerprint;

}
