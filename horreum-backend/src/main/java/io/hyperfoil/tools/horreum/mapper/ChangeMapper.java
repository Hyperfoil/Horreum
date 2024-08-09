package io.hyperfoil.tools.horreum.mapper;

import io.hyperfoil.tools.horreum.api.alerting.Change;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;

public class ChangeMapper {

    public static Change from(ChangeDAO c) {
        Change dto = new Change();
        dto.id = c.id;
        dto.variable = VariableMapper.from(c.variable);
        dto.dataset = DatasetMapper.from(c.dataset);
        dto.timestamp = c.timestamp;
        dto.confirmed = c.confirmed;
        dto.description = c.description;

        return dto;
    }

    public static ChangeDAO to(Change c) {
        ChangeDAO dao = new ChangeDAO();
        dao.id = c.id;
        dao.variable = VariableMapper.to(c.variable);
        dao.dataset = DatasetMapper.to(c.dataset, null);

        dao.timestamp = c.timestamp;
        dao.confirmed = c.confirmed;
        dao.description = c.description;

        return dao;
    }
}
