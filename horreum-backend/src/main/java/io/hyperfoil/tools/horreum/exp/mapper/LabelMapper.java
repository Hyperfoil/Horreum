package io.hyperfoil.tools.horreum.exp.mapper;

import java.util.stream.Collectors;

import io.hyperfoil.tools.horreum.api.exp.data.Label;
import io.hyperfoil.tools.horreum.exp.data.LabelDao;

public class LabelMapper {
    public static Label from(LabelDao l) {
        if (l == null)
            return null;
        Label dto = new Label();
        dto.id = l.id;
        dto.name = l.name;
        dto.scalarMethod = l.scalarMethod;
        dto.multiType = l.multiType;
        //        dto.parent = TestMapper.from(l.parent);
        dto.reducer = LabelReducerMapper.from(l.reducer);
        dto.extractors = l.extractors.stream().map(ExtractorMapper::from).collect(Collectors.toList());

        return dto;
    }

    //Why do we have a mapper, won't that lose the fields that are not part of the api?
    //should all fields be part of the API?
    public static LabelDao to(Label dto) {
        if (dto == null) {
            return null;
        }
        LabelDao l = new LabelDao();
        l.id = dto.id;
        l.name = dto.name;
        l.scalarMethod = dto.scalarMethod;
        l.multiType = dto.multiType;
        l.dirty = dto.dirty;
        l.sourceLabel = LabelMapper.to(dto.sourceLabel);
        l.sourceGroup = LabelGroupMapper.to(dto.sourceGroup);
        l.group = LabelGroupMapper.to(dto.group);
        l.targetGroup = LabelGroupMapper.to(dto.targetGroup);
        l.splitting = dto.splitting;

        l.reducer = LabelReducerMapper.to(dto.reducer);

        l.extractors = dto.extractors.stream().map(ExtractorMapper::to).collect(Collectors.toList());

        return l;
    }
}
