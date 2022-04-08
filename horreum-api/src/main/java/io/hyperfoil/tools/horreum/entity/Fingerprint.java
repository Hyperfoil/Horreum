package io.hyperfoil.tools.horreum.entity;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.hyperfoil.tools.horreum.entity.json.DataSet;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Immutable
public class Fingerprint extends PanacheEntityBase {
   @Id
   @Column(name = "dataset_id")
   public int datasetId;

   @OneToOne(fetch = FetchType.LAZY)
   @MapsId
   @JoinColumn(name = "dataset_id")
   public DataSet dataset;

   @Type(type = "io.hyperfoil.tools.horreum.entity.converter.JsonUserType")
   public JsonNode fingerprint;

}
