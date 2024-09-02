package io.hyperfoil.tools.horreum.mapper;

import java.util.stream.Collectors;

import io.hyperfoil.tools.horreum.api.data.ExperimentComparison;
import io.hyperfoil.tools.horreum.api.data.ExperimentProfile;
import io.hyperfoil.tools.horreum.entity.ExperimentComparisonDAO;
import io.hyperfoil.tools.horreum.entity.ExperimentProfileDAO;

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

        dto.comparisons = ep.comparisons.stream().map(ExperimentProfileMapper::fromExperimentComparison)
                .collect(Collectors.toList());

        return dto;
    }

    public static ExperimentComparison fromExperimentComparison(ExperimentComparisonDAO ec) {
        ExperimentComparison dto = new ExperimentComparison();
        if (ec.variable != null) {
            dto.variableId = ec.variable.id;
            dto.variableName = ec.variable.name;
        }
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

        ep.comparisons = dto.comparisons.stream().map(ExperimentProfileMapper::toExperimentComparison)
                .collect(Collectors.toList());

        return ep;
    }

    public static ExperimentComparisonDAO toExperimentComparison(ExperimentComparison dto) {
        ExperimentComparisonDAO ec = new ExperimentComparisonDAO();
        ec.config = dto.config;
        ec.model = dto.model;
        if (dto.variableId != null && dto.variableId > 0)
            ec.setVariableId(dto.variableId);
        return ec;
    }
}
