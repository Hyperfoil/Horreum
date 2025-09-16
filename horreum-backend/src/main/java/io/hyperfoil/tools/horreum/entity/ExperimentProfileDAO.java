package io.hyperfoil.tools.horreum.entity;

import java.util.List;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.JsonNode;

import io.hyperfoil.tools.horreum.entity.data.TestDAO;
import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity(name = "ExperimentProfile")
@Table(name = "experiment_profile")
public class ExperimentProfileDAO extends PanacheEntityBase {
    @Id
    @SequenceGenerator(name = "experimentprofileidgenerator", sequenceName = "experimentprofileidgenerator", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "experimentprofileidgenerator")
    public Integer id;

    @NotNull
    public String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    public TestDAO test;

    @Column(name = "selector_labels", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    public JsonNode selectorLabels;

    @Column(name = "selector_filter")
    public String selectorFilter;

    @Column(name = "baseline_labels", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    public JsonNode baselineLabels;

    @Column(name = "baseline_filter")
    public String baselineFilter;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "experiment_comparisons", joinColumns = @JoinColumn(name = "profile_id"))
    @OrderBy("variable_id, model")
    public List<ExperimentComparisonDAO> comparisons;

    /* These labels are not used in Horreum but are added to the result event */
    @Column(name = "extra_labels", columnDefinition = "jsonb")
    @Type(JsonBinaryType.class)
    public JsonNode extraLabels;

}
