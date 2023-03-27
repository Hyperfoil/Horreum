package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.data.ExperimentComparison;
import io.hyperfoil.tools.horreum.entity.ExperimentProfileDAO;
import io.hyperfoil.tools.horreum.api.data.ExperimentProfile;

import java.util.stream.Collectors;

public class ExperimentProfileMapper {

    public static ExperimentProfile from(ExperimentProfileDAO ep) {
        ExperimentProfile dto = new ExperimentProfile();
        dto.id = ep.id;
        dto.name = ep.name;
        dto.testId = ep.test.id;
        dto.baselineLabels = ep.baselineLabels;
        dto.extraLabels = ep.extraLabels;
        dto.selectorLabels = ep.selectorLabels;
        dto.baselineFilter = ep.baselineFilter;
        dto.selectorFilter = ep.selectorFilter;

        dto.comparisons = ep.comparisons.stream().map(ExperimentProfileMapper::fromExperimentComparison).collect(Collectors.toList());

        return dto;
    }

    public static ExperimentComparison fromExperimentComparison(io.hyperfoil.tools.horreum.entity.ExperimentComparison ec) {
        ExperimentComparison dto = new ExperimentComparison();
        dto.variableId = ec.getVariableId();
        dto.variableName = ec.variable.name;
        dto.config = ec.config;
        dto.model = ec.model;

        return dto;
    }

    public static ExperimentProfileDAO to(ExperimentProfile dto) {
        ExperimentProfileDAO ep = new ExperimentProfileDAO();
        ep.id = dto.id;
        ep.name = dto.name;
        ep.baselineLabels = dto.baselineLabels;
        ep.extraLabels = dto.extraLabels;
        ep.selectorLabels = dto.selectorLabels;
        ep.baselineFilter = dto.baselineFilter;
        ep.selectorFilter = dto.selectorFilter;

        ep.comparisons = dto.comparisons.stream().map(ExperimentProfileMapper::toExperimentComparison).collect(Collectors.toList());

        return ep;
    }

    public static io.hyperfoil.tools.horreum.entity.ExperimentComparison toExperimentComparison(ExperimentComparison dto) {
        io.hyperfoil.tools.horreum.entity.ExperimentComparison ec = new io.hyperfoil.tools.horreum.entity.ExperimentComparison();
        ec.config = dto.config;
        ec.model = dto.model;
        ec.setVariableId(dto.variableId);

        return ec;
    }
}
