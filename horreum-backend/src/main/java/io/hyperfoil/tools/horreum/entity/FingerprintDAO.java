package io.hyperfoil.tools.horreum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "Fingerprint")
public class FingerprintDAO extends PanacheEntityBase {
    @Id
    @Column(name = "dataset_id")
    public Integer datasetId;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public JsonNode fingerprint;

    @Column(name = "fp_hash")
    public Integer fpHash;

    @PrePersist
    @PreUpdate
    // guarantees the hash is computed before persisting the fingerprint
    public void populateHash() {
        this.fpHash = fingerprint != null ? fingerprint.hashCode() : null;
    }

    @Override
    public String toString() {
        return "FP{" +
                "datasetId=" + datasetId +
                ", fingerprint=" + fingerprint +
                ", fpHash=" + fpHash +
                '}';
    }
}
