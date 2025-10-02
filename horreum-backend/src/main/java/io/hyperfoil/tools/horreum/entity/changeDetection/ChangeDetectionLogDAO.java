package io.hyperfoil.tools.horreum.entity.changeDetection;

import jakarta.persistence.*;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.PersistentLogDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;

/**
 */
@Entity(name = "ChangeDetectionLog")
public class ChangeDetectionLogDAO extends PersistentLogDAO {

    @Id
    @SequenceGenerator(name = "changedetectionlog_id_generator", sequenceName = "changedetectionlog_id_generator", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "changedetectionlog_id_generator")
    public Long id;

    @Column(name = "variableid")
    public int variableId;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public JsonNode fingerprint;

    public ChangeDetectionLogDAO() {
        super(0, null);
    }

    public ChangeDetectionLogDAO(int variableId, JsonNode fingerprint, int level, String message) {
        super(level, message);
        this.variableId = variableId;
        this.fingerprint = fingerprint;
    }

    public JsonNode getFingerprint() {
        return fingerprint;
    }
}
