package io.hyperfoil.tools.horreum.entity.data;

import java.util.Collection;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.api.data.Access;
import io.hyperfoil.tools.horreum.entity.backend.DatastoreConfigDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "test")
@JsonIgnoreType
public class TestDAO extends PanacheEntityBase {
    @Id
    @SequenceGenerator(name = "test_id_seq", sequenceName = "test_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "test_id_seq")
    @Column(name = "id")
    public Integer id;

    @NotNull
    @Column(name = "name", unique = true)
    public String name;

    public String folder;

    @Column(name = "description", unique = false)
    public String description;

    @ManyToOne(fetch = FetchType.EAGER)
    public DatastoreConfigDAO backendConfig;

    @NotNull
    public String owner;

    @NotNull
    @JdbcTypeCode(SqlTypes.INTEGER)
    public Access access = Access.PUBLIC;

    @Column(name = "timeline_labels")
    @Type(JsonBinaryType.class)
    public JsonNode timelineLabels;

    @Column(name = "timeline_function")
    public String timelineFunction;

    @Column(name = "fingerprint_labels")
    @Type(JsonBinaryType.class)
    public JsonNode fingerprintLabels;

    @Column(name = "fingerprint_filter")
    public String fingerprintFilter;

    @NotNull
    @OneToMany(fetch = FetchType.EAGER, cascade = { CascadeType.ALL }, orphanRemoval = true, mappedBy = "test")
    @Fetch(FetchMode.SELECT)
    public Collection<ViewDAO> views;

    public String compareUrl;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "test_transformers", joinColumns = @JoinColumn(name = "test_id"), inverseJoinColumns = @JoinColumn(name = "transformer_id"))
    @Fetch(FetchMode.SELECT)
    public Collection<TransformerDAO> transformers;

    @NotNull
    @Column(columnDefinition = "boolean default true")
    public Boolean notificationsEnabled;

    public void ensureLinked() {
        if (views != null) {
            views.forEach(v -> {
                v.test = this;
                v.ensureLinked();
            });
        }
    }
}
