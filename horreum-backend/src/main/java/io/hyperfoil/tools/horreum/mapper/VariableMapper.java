package io.hyperfoil.tools.horreum.mapper;

import java.util.ArrayList;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.hyperfoil.tools.horreum.api.alerting.Variable;
import io.hyperfoil.tools.horreum.entity.alerting.VariableDAO;
import io.hyperfoil.tools.horreum.entity.data.LabelDAO;

public class VariableMapper {

    public static Variable from(VariableDAO dao) {
        Variable v = new Variable();
        v.id = dao.id;
        v.testId = dao.testId;
        v.name = dao.name;
        v.group = dao.group;
        v.order = dao.order;
        v.calculation = dao.calculation;
        v.changeDetection = dao.changeDetection.stream().map(ChangeDetectionMapper::from).collect(Collectors.toSet());
        //label
        if (dao.labels.isArray()) {
            v.labels = new ArrayList<>();
            dao.labels.spliterator().forEachRemaining(n -> {
                LabelDAO l = LabelDAO.find("name", n.asText()).firstResult();
                if (l != null)
                    v.labels.add(LabelMapper.from(l).name);
            });
        }
        return v;
    }

    public static VariableDAO to(Variable dto) {
        VariableDAO v = new VariableDAO();
        v.id = dto.id;
        v.testId = dto.testId;
        v.name = dto.name;
        v.group = dto.group;
        v.order = dto.order;
        if (dto.labels != null && !dto.labels.isEmpty()) {
            ArrayNode n = JsonNodeFactory.instance.arrayNode();
            for (String l : dto.labels)
                n.add(l);
            v.labels = n;
        } else {
            v.labels = JsonNodeFactory.instance.arrayNode();
        }
        v.calculation = dto.calculation;
        if (dto.changeDetection != null)
            v.changeDetection = dto.changeDetection.stream().map(ChangeDetectionMapper::to).collect(Collectors.toSet());

        return v;
    }
}
