package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.alerting.ChangeDetectionDAO;
import io.hyperfoil.tools.horreum.api.alerting.ChangeDetection;
import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import io.hyperfoil.tools.horreum.api.alerting.Variable;

import java.util.stream.Collectors;

public class VariableMapper {

    public static Variable from(VariableDAO variable) {
        return new Variable(variable.id, variable.testId, variable.name, variable.group,
                variable.order, variable.labels, variable.calculation,
                variable.changeDetection.stream().map(ChangeDetectionMapper::from).collect(Collectors.toSet())
        );
    }

    public static VariableDAO to(Variable dto) {
        VariableDAO v = new VariableDAO();
        v.id = dto.id;
        v.testId = dto.testId;
        v.name = dto.name;
        v.group = dto.group;
        v.order = dto.order;
        v.labels = dto.labels;
        v.calculation = dto.calculation;
        if(dto.changeDetection != null)
            v.changeDetection = dto.changeDetection.stream().map(ChangeDetectionMapper::to).collect(Collectors.toSet());

        return v;
    }
}

