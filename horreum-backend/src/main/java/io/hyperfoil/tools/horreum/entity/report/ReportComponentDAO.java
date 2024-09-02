package io.hyperfoil.tools.horreum.entity.report;

import static jakarta.persistence.GenerationType.SEQUENCE;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import org.hibernate.annotations.Type;

import com.fasterxml.jackson.databind.node.ArrayNode;

import io.hyperfoil.tools.horreum.hibernate.JsonBinaryType;

@Entity(name = "ReportComponent")
@Table(name = "reportcomponent")
public class ReportComponentDAO {
    @Id
    @SequenceGenerator(name = "reportComponentIdGenerator", sequenceName = "reportcomponent_seq")
    @GeneratedValue(strategy = SEQUENCE, generator = "reportComponentIdGenerator")
    public Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reportconfig_id")
    public TableReportConfigDAO report;

    @NotNull
    public String name;

    @NotNull
    @Column(name = "component_order")
    public int order;

    @NotNull
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    public ArrayNode labels;

    public String function;

    // displayed on Y axis
    public String unit;
}
