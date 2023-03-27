package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.api.alerting.Change;

public class ChangeMapper {

    public static Change from(ChangeDAO c) {
        Change dto = new Change();
        dto.id = c.id;
        dto.variable = VariableMapper.from(c.variable);
        dto.dataset = DataSetMapper.from(c.dataset);
        dto.timestamp = c.timestamp;
        dto.confirmed = c.confirmed;
        dto.description = c.description;

        return dto;
    }

}
