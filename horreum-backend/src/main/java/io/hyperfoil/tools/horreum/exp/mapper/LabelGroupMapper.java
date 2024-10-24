package io.hyperfoil.tools.horreum.exp.mapper;

import java.util.stream.Collectors;

import io.hyperfoil.tools.horreum.api.exp.data.LabelGroup;
import io.hyperfoil.tools.horreum.exp.data.LabelGroupDao;

public class LabelGroupMapper {

    public static LabelGroup from(LabelGroupDao dao) {
        LabelGroup rtrn = new LabelGroup();
        rtrn.name = dao.name;
        rtrn.owner = dao.owner;
        rtrn.labels = dao.labels.stream().map(LabelMapper::from).collect(Collectors.toList());
        return rtrn;
    }

    public static LabelGroupDao to(LabelGroup dto) {
        LabelGroupDao rtrn = new LabelGroupDao();
        rtrn.id = dto.id;
        rtrn.name = dto.name;
        rtrn.labels = dto.labels.stream().map(LabelMapper::to).collect(Collectors.toList());
        return rtrn;
    }
}
